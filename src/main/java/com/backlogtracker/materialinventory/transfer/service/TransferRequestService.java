package com.backlogtracker.materialinventory.transfer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.web.ScopedLookup;
import com.backlogtracker.materialinventory.QuarterStep;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.transfer.domain.LineStatus;
import com.backlogtracker.materialinventory.transfer.domain.TransferRequest;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.SendShipmentRequest;
import com.backlogtracker.materialinventory.transfer.dto.TransferRequestView;
import com.backlogtracker.materialinventory.transfer.repository.TransferRequestRepository;
import com.backlogtracker.materialinventory.yarn.domain.YarnType;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

import lombok.RequiredArgsConstructor;

/**
 * A targeted request for yarn from one member to another, covering one or more yarn types
 * (each its own line) — see {@link TransferRequest} for why sending and confirming receipt
 * are two separate steps, and why a line's own status (not a single request-wide status) is
 * what actually gates further sends. Visible business-wide (design decision, same
 * full-transparency precedent used elsewhere in this applet), but only the requester or the
 * target may act on any given line, matching who that action concerns.
 */
@Service
@RequiredArgsConstructor
public class TransferRequestService {

    private static final String LINK_PATH = "/materialinventory/requests";

    /** How many times {@link #send}/{@link #confirmReceived} re-read and retry after losing
     *  an optimistic-locking race on this document (see {@code TransferRequest.version}).
     *  Comfortably covers realistic contention (a handful of members acting on the same
     *  request around the same time) — a losing attempt re-reads the now-current state, so
     *  a retry that finds the real cap already reached still correctly fails as a business
     *  rejection (400), not a swallowed race. */
    private static final int MAX_VERSION_RETRIES = 50;

    private final TransferRequestRepository repository;
    private final GroupService groupService;
    private final YarnTypeService yarnTypeService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final Clock clock;

    public List<TransferRequestView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(TransferRequestView::of).toList();
    }

    public TransferRequestView create(String groupId, String userId, CreateTransferRequestRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        if (request.targetUserId() == null || request.targetUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUserId is required");
        }
        if (request.targetUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request yarn from yourself");
        }
        if (!group.hasMember(request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUserId must be a member of this inventory group");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one yarn type is required");
        }

        List<TransferRequest.TransferLine> lines = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        Set<String> seenYarnTypes = new HashSet<>();
        for (CreateTransferRequestRequest.LineInput lineInput : request.lines()) {
            if (!seenYarnTypes.add(lineInput.yarnTypeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each yarn type can only appear once per request");
            }
            YarnType yarnType = yarnTypeService.requireById(groupId, lineInput.yarnTypeId());
            double quantity = QuarterStep.require(lineInput.quantity());
            if (quantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
            }
            // Prevents raising a request against someone who plainly doesn't have it — the
            // real "not enough on hand" guard still lives in send() (their stock can change
            // between now and then), this is just stopping the obviously pointless ask up
            // front (design decision from real use: don't let me ask someone with 0 skeins
            // for 1).
            double targetHas = inventoryService.quantityOf(groupId, request.targetUserId(), lineInput.yarnTypeId());
            if (targetHas + QuarterStep.EPSILON < quantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        yarnType.getBrand() + " " + yarnType.getThickness() + " (" + yarnType.getColour() + "): "
                                + "that member only has " + targetHas + " on hand, not enough for this request");
            }
            lines.add(TransferRequest.TransferLine.builder()
                    .lineId(UUID.randomUUID().toString())
                    .yarnTypeId(lineInput.yarnTypeId())
                    .requestedQuantity(quantity)
                    .status(LineStatus.OPEN)
                    .shipments(new ArrayList<>())
                    .build());
            summary.add(quantity + " " + yarnType.getBrand() + " " + yarnType.getThickness() + " (" + yarnType.getColour() + ")");
        }

        TransferRequest saved = repository.save(TransferRequest.builder()
                .groupId(groupId)
                .requesterId(userId)
                .targetUserId(request.targetUserId())
                .lines(lines)
                .createdAt(Instant.now(clock))
                .build());
        notificationService.actionable(request.targetUserId(), NotificationType.TRANSFER_REQUEST_CREATED,
                "Yarn request", "Someone is asking you for " + String.join(", ", summary) + ".", LINK_PATH, saved.getId());
        return TransferRequestView.of(saved);
    }

    /** Only the target — the one who actually holds the yarn — may send against a line, in
     *  one or more installments (design decision: a target may not have everything on hand
     *  at once, e.g. "forgot the black ones, brings them tomorrow"). Debits the target's own
     *  on-hand immediately, same guarantee {@code InventoryService.adjustQuantity} already
     *  enforces ("the holder can only give away what they actually have") — the yarn sits in
     *  neither party's inventory until the requester separately confirms it arrived. */
    public TransferRequestView send(String groupId, String userId, String requestId, String lineId, SendShipmentRequest request) {
        groupService.requireMember(groupId, userId);
        double amount = QuarterStep.require(request.quantity());
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }

        TransferRequest tr = null;
        TransferRequest saved = null;
        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            tr = requireById(groupId, requestId);
            if (!tr.getTargetUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person holding the yarn can send against this request");
            }
            TransferRequest.TransferLine line = requireLine(tr, lineId);
            if (line.getStatus() != LineStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This line is closed — the requester no longer wants more of this yarn type from you");
            }
            double remaining = line.getRequestedQuantity() - sentQuantity(line);
            if (amount > remaining + QuarterStep.EPSILON) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That's more than is still requested (" + remaining + " remaining)");
            }

            // Debits before any local mutation, so a rejection here (not enough on hand)
            // leaves this request untouched — nothing to roll back.
            inventoryService.adjustQuantity(groupId, userId, line.getYarnTypeId(), -amount);
            line.getShipments().add(TransferRequest.Shipment.builder()
                    .shipmentId(UUID.randomUUID().toString())
                    .quantity(amount)
                    .notes(blankToNull(request.notes()))
                    .sentAt(Instant.now(clock))
                    .build());
            try {
                saved = repository.save(tr);
                break;
            } catch (OptimisticLockingFailureException e) {
                // The debit above already went through but this save lost the race (someone
                // else changed the same request in between) — credit it straight back and
                // retry against a fresh read, rather than leave yarn debited with no
                // shipment ever recorded for it.
                inventoryService.adjustQuantity(groupId, userId, line.getYarnTypeId(), amount);
            }
        }
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is being updated by someone else — please retry");
        }

        notificationService.resolveOneByReference(NotificationType.TRANSFER_REQUEST_CREATED, requestId, userId, true);
        String noteSuffix = request.notes() != null && !request.notes().isBlank() ? " — " + request.notes().trim() : "";
        notificationService.info(tr.getRequesterId(), NotificationType.TRANSFER_REQUEST_RESOLVED,
                "Yarn request", amount + " was sent your way" + noteSuffix + ".", LINK_PATH);
        return TransferRequestView.of(saved);
    }

    /** Only the requester may confirm a shipment arrived — the moment that specific
     *  quantity actually credits their own inventory. Independent per shipment, so
     *  receiving half of a request today and the rest next week are two ordinary,
     *  separately-confirmable events. */
    public TransferRequestView confirmReceived(String groupId, String userId, String requestId, String lineId, String shipmentId) {
        groupService.requireMember(groupId, userId);

        TransferRequest tr = null;
        TransferRequest saved = null;
        double quantity = 0;
        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            tr = requireById(groupId, requestId);
            if (!tr.getRequesterId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can confirm a shipment received");
            }
            TransferRequest.TransferLine line = requireLine(tr, lineId);
            TransferRequest.Shipment shipment = line.getShipments().stream()
                    .filter(s -> s.getShipmentId().equals(shipmentId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
            if (shipment.getReceivedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This shipment was already confirmed received");
            }

            quantity = shipment.getQuantity();
            shipment.setReceivedAt(Instant.now(clock));
            inventoryService.adjustQuantity(groupId, userId, line.getYarnTypeId(), quantity);
            try {
                saved = repository.save(tr);
                break;
            } catch (OptimisticLockingFailureException e) {
                inventoryService.adjustQuantity(groupId, userId, line.getYarnTypeId(), -quantity);
            }
        }
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is being updated by someone else — please retry");
        }

        notificationService.info(tr.getTargetUserId(), NotificationType.TRANSFER_REQUEST_RESOLVED,
                "Yarn request", "Your shipment of " + quantity + " was confirmed received.", LINK_PATH);
        return TransferRequestView.of(saved);
    }

    /** The requester can stop expecting any more of one yarn type from this target at any
     *  time — whether nothing was ever sent, or only part of what was asked for arrived
     *  (design decision from real use: "I got 2 of the 5 I asked for, that's enough, I'll
     *  get the rest elsewhere"). Never touches a shipment already sent — closing only blocks
     *  new ones; anything already in transit stays confirmable whenever it arrives. */
    public TransferRequestView closeLine(String groupId, String userId, String requestId, String lineId) {
        groupService.requireMember(groupId, userId);

        TransferRequest tr = null;
        TransferRequest saved = null;
        double remaining = 0;
        // Round 5 review: unlike send/confirmReceived, this never touches inventory before
        // saving, so a lost race just needs a fresh re-read and re-apply — no compensating
        // credit/debit to undo first.
        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            tr = requireById(groupId, requestId);
            if (!tr.getRequesterId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can close a line");
            }
            TransferRequest.TransferLine line = requireLine(tr, lineId);
            if (line.getStatus() == LineStatus.CLOSED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This line is already closed");
            }
            remaining = line.getRequestedQuantity() - sentQuantity(line);
            line.setStatus(LineStatus.CLOSED);
            try {
                saved = repository.save(tr);
                break;
            } catch (OptimisticLockingFailureException e) {
                // retry against a fresh read
            }
        }
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is being updated by someone else — please retry");
        }

        if (remaining > QuarterStep.EPSILON) {
            notificationService.info(tr.getTargetUserId(), NotificationType.TRANSFER_REQUEST_RESOLVED,
                    "Yarn request", "The remaining amount you hadn't sent yet is no longer needed.", LINK_PATH);
        }
        return TransferRequestView.of(saved);
    }

    /** Only when nothing has been sent anywhere on the request yet — once any shipment
     *  exists on any line, a blanket cancel can no longer undo it, so {@link #closeLine}
     *  per line is the only way to stop the rest. */
    public TransferRequestView cancel(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);

        TransferRequest saved = null;
        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            TransferRequest tr = requireById(groupId, requestId);
            if (!tr.getRequesterId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel this request");
            }
            boolean anythingSent = tr.getLines().stream().anyMatch(l -> !l.getShipments().isEmpty());
            if (anythingSent) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Some yarn has already been sent on this request — close individual lines instead");
            }
            tr.getLines().forEach(l -> l.setStatus(LineStatus.CLOSED));
            try {
                saved = repository.save(tr);
                break;
            } catch (OptimisticLockingFailureException e) {
                // retry against a fresh read
            }
        }
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is being updated by someone else — please retry");
        }
        notificationService.resolveByReference(NotificationType.TRANSFER_REQUEST_CREATED, requestId, false);
        return TransferRequestView.of(saved);
    }

    private static double sentQuantity(TransferRequest.TransferLine line) {
        return line.getShipments().stream().mapToDouble(TransferRequest.Shipment::getQuantity).sum();
    }

    private TransferRequest requireById(String groupId, String requestId) {
        return ScopedLookup.requireInGroup(repository.findById(requestId), TransferRequest::getGroupId, groupId, "Transfer request not found");
    }

    private static TransferRequest.TransferLine requireLine(TransferRequest tr, String lineId) {
        return tr.getLines().stream().filter(l -> l.getLineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
