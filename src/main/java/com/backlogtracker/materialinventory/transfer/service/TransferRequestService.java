package com.backlogtracker.materialinventory.transfer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

    private final TransferRequestRepository repository;
    private final MongoOperations mongo;
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
     *  <p>The "no more than requested" cap is enforced by {@link #reserveFulfillment} as a
     *  single atomic conditional update — not a read-then-write — so two concurrent
     *  fulfillments of the same request can never together push {@code fulfilledQuantity}
     *  past {@code requestedQuantity}, closing the race an earlier version of this method
     *  left open (round 4 review flagged it as needing a real database transaction to fix;
     *  it doesn't, since {@code requestedQuantity} is never mutated after creation — that
     *  makes "not too much left" a plain atomic range filter, the same technique
     *  {@code InventoryService.adjustQuantity} already uses for the physical quantity
     *  itself). The reservation runs before the physical inventory move: if the inventory
     *  move then fails (e.g. the target's on-hand quantity changed in the interim via some
     *  other concurrent action), the reservation is rolled back so the request's own
     *  bookkeeping never claims more was handed over than actually was. */
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

        TransferRequest reserved = reserveFulfillment(groupId, requestId, amount, request.getRequestedQuantity());
        try {
            inventoryService.adjustQuantity(groupId, request.getTargetUserId(), request.getYarnTypeId(), -amount);
            inventoryService.adjustQuantity(groupId, request.getRequesterId(), request.getYarnTypeId(), amount);
        } catch (RuntimeException e) {
            unreserve(groupId, requestId, amount);
            throw e;
        }
        return TransferRequestView.of(reserved);
    }

    /** Atomically increments {@code fulfilledQuantity} only if doing so wouldn't exceed
     *  {@code requestedQuantity} — the query filter itself encodes the cap
     *  ({@code fulfilledQuantity <= requestedQuantity - amount}), so a concurrent call that
     *  would blow the budget simply doesn't match and gets a clean rejection instead of a
     *  lost-update race. {@code requestedQuantity} is passed in from the caller's own
     *  already-fresh read rather than re-fetched, since it's immutable after {@link #create}
     *  ever sets it. */
    private TransferRequest reserveFulfillment(String groupId, String requestId, double amount, double requestedQuantity) {
        double maxFulfilledBefore = requestedQuantity - amount + QUARTER_STEP_EPSILON;
        Query query = Query.query(Criteria.where("id").is(requestId).and("groupId").is(groupId)
                .and("status").in(TransferStatus.PENDING, TransferStatus.PARTIALLY_FULFILLED)
                .and("fulfilledQuantity").lte(maxFulfilledBefore));
        Update update = new Update().inc("fulfilledQuantity", amount).set("status", TransferStatus.PARTIALLY_FULFILLED);
        TransferRequest updated = mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), TransferRequest.class);
        if (updated == null) {
            // requireOpenRequest() throws its own (already-resolved) error if that's why the
            // update above didn't match, so this message is only reached when the real cause
            // is "not enough left".
            TransferRequest current = requireOpenRequest(groupId, requestId);
            double remaining = current.getRequestedQuantity() - current.getFulfilledQuantity();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That's more than is still requested (" + remaining + " remaining)");
        }
        return updated;
    }

    /** Compensates a successful reservation when the physical inventory move that was
     *  supposed to follow it fails — without this, a failed transfer would still show as
     *  partially fulfilled on the request even though no yarn actually moved. Not itself
     *  atomic with the reservation (no real transaction backs this), but this is the rare
     *  failure path, not the contended common case the atomic reservation above protects. */
    private void unreserve(String groupId, String requestId, double amount) {
        Query query = Query.query(Criteria.where("id").is(requestId).and("groupId").is(groupId));
        mongo.updateFirst(query, new Update().inc("fulfilledQuantity", -amount), TransferRequest.class);
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
