package com.backlogtracker.materialinventory.transfer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.transfer.domain.TransferRequest;
import com.backlogtracker.materialinventory.transfer.domain.TransferStatus;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.TransferRequestView;
import com.backlogtracker.materialinventory.transfer.repository.TransferRequestRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A targeted request for yarn from one member to another — see {@link TransferRequest}
 * for why fulfillment and completion are two separate steps. Visible business-wide
 * (design decision, same full-transparency precedent used elsewhere in this applet), but
 * only the two parties named on a request may act on it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferRequestService {

    private static final double QUARTER_STEP_EPSILON = 1e-9;
    // Higher than CostConfigChangeService's equivalent retry cap: that request is contended
    // by at most a handful of group members approving once each, while a transfer request
    // can legitimately be fulfilled in many small installments in quick succession.
    private static final int MAX_RESERVE_RETRIES = 20;

    private final TransferRequestRepository repository;
    private final GroupService groupService;
    private final YarnTypeService yarnTypeService;
    private final InventoryService inventoryService;
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
        yarnTypeService.requireById(groupId, request.yarnTypeId());
        double requestedQuantity = requireQuarterStep(request.requestedQuantity());
        if (requestedQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestedQuantity must be greater than zero");
        }

        TransferRequest saved = repository.save(TransferRequest.builder()
                .groupId(groupId)
                .requesterId(userId)
                .targetUserId(request.targetUserId())
                .yarnTypeId(request.yarnTypeId())
                .requestedQuantity(requestedQuantity)
                .fulfilledQuantity(0)
                .status(TransferStatus.PENDING)
                .createdAt(Instant.now(clock))
                .build());
        return TransferRequestView.of(saved);
    }

    /** Only the target member — the one who actually holds the yarn — may fulfill any
     *  amount of it, up to what's still requested and what they actually have on hand
     *  (design decision: allow partial fulfillment, one or more installments). This moves
     *  the given quantity out of the target's own on-hand inventory into the requester's.
     *
     *  <p>{@code request.fulfilledQuantity} is bumped in {@link #recordFulfillment} with
     *  optimistic-lock retry (same pattern as {@code CostConfigChangeService.approve}), so
     *  two concurrent fulfillments of the same request can't lose one another's update. The
     *  actual inventory movement runs through {@link InventoryService#adjustQuantity}, which
     *  is independently atomic against the physical stock ever going negative — that's the
     *  one invariant that must never break, so it isn't retried here alongside the request
     *  bookkeeping (retrying it would double-move yarn on a request-side retry). */
    public TransferRequestView fulfill(String groupId, String userId, String requestId, double quantity) {
        groupService.requireMember(groupId, userId);
        TransferRequest request = requireOpenRequest(groupId, requestId);
        if (!request.getTargetUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person holding the yarn can fulfill this request");
        }
        double amount = requireQuarterStep(quantity);
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
        double remaining = request.getRequestedQuantity() - request.getFulfilledQuantity();
        if (amount - remaining > QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That's more than is still requested (" + remaining + " remaining)");
        }

        inventoryService.adjustQuantity(groupId, request.getTargetUserId(), request.getYarnTypeId(), -amount);
        inventoryService.adjustQuantity(groupId, request.getRequesterId(), request.getYarnTypeId(), amount);

        return TransferRequestView.of(recordFulfillment(groupId, requestId, amount));
    }

    private TransferRequest recordFulfillment(String groupId, String requestId, double amount) {
        for (int attempt = 1; attempt <= MAX_RESERVE_RETRIES; attempt++) {
            TransferRequest request = requireOpenRequest(groupId, requestId);
            request.setFulfilledQuantity(request.getFulfilledQuantity() + amount);
            if (request.getStatus() == TransferStatus.PENDING) {
                request.setStatus(TransferStatus.PARTIALLY_FULFILLED);
            }
            try {
                return repository.save(request);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RESERVE_RETRIES) {
                    throw e;
                }
                log.info("Transfer fulfillment race on request {} — retrying (attempt {})", requestId, attempt);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** Only the target may mark a request complete (design decision), whether or not it
     *  was ever fully fulfilled — the receiver is the one who knows whether they're done
     *  giving yarn out against this ask. */
    public TransferRequestView markComplete(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);
        TransferRequest request = requireOpenRequest(groupId, requestId);
        if (!request.getTargetUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person holding the yarn can complete this request");
        }
        request.setStatus(TransferStatus.COMPLETED);
        request.setResolvedAt(Instant.now(clock));
        return TransferRequestView.of(repository.save(request));
    }

    /** The requester can withdraw their own ask while it's still open. */
    public TransferRequestView cancel(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);
        TransferRequest request = requireOpenRequest(groupId, requestId);
        if (!request.getRequesterId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel this request");
        }
        request.setStatus(TransferStatus.CANCELLED);
        request.setResolvedAt(Instant.now(clock));
        return TransferRequestView.of(repository.save(request));
    }

    private TransferRequest requireOpenRequest(String groupId, String requestId) {
        TransferRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer request not found"));
        if (!groupId.equals(request.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer request not found");
        }
        if (request.getStatus() == TransferStatus.COMPLETED || request.getStatus() == TransferStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already " + request.getStatus());
        }
        return request;
    }

    private double requireQuarterStep(double quantity) {
        double quarters = quantity * 4;
        if (Math.abs(quarters - Math.round(quarters)) > QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be in quarter-skein steps (e.g. 0.25, 1.5)");
        }
        return Math.round(quarters) / 4.0;
    }
}
