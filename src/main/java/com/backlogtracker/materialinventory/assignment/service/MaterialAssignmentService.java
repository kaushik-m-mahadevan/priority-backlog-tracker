package com.backlogtracker.materialinventory.assignment.service;

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
import com.backlogtracker.commons.web.ScopedLookup;
import com.backlogtracker.materialinventory.QuarterStep;
import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignment;
import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignmentStatus;
import com.backlogtracker.materialinventory.assignment.dto.CreateMaterialAssignmentRequest;
import com.backlogtracker.materialinventory.assignment.dto.MaterialAssignmentView;
import com.backlogtracker.materialinventory.assignment.repository.MaterialAssignmentRepository;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

import lombok.RequiredArgsConstructor;

/**
 * mb-21: propose/accept yarn hand-off between two named members — see
 * {@link MaterialAssignment} for why the debit happens at propose time, not accept time.
 * Visible business-wide (same full-transparency precedent as {@code TransferRequestService}),
 * but only the two named parties on an assignment may act on it.
 */
@Service
@RequiredArgsConstructor
public class MaterialAssignmentService {

    private final MaterialAssignmentRepository repository;
    private final MongoOperations mongo;
    private final GroupService groupService;
    private final YarnTypeService yarnTypeService;
    private final InventoryService inventoryService;
    private final Clock clock;

    public List<MaterialAssignmentView> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(MaterialAssignmentView::of).toList();
    }

    /** Debits the proposer's own on-hand immediately (via {@link InventoryService#adjustQuantity},
     *  which rejects if they don't have enough) so a pending assignment can never be
     *  double-counted as still theirs — same "the holder can only give away what they
     *  actually have" guarantee {@code InventoryService.withdraw} already enforces. */
    public MaterialAssignmentView propose(String groupId, String userId, CreateMaterialAssignmentRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        if (request.recipientId() == null || request.recipientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientId is required");
        }
        if (request.recipientId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign yarn to yourself");
        }
        if (!group.hasMember(request.recipientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientId must be a member of this inventory group");
        }
        yarnTypeService.requireById(groupId, request.yarnTypeId());
        double quantity = QuarterStep.require(request.quantity());
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }

        inventoryService.adjustQuantity(groupId, userId, request.yarnTypeId(), -quantity);
        MaterialAssignment saved = repository.save(MaterialAssignment.builder()
                .groupId(groupId)
                .proposerId(userId)
                .recipientId(request.recipientId())
                .yarnTypeId(request.yarnTypeId())
                .quantity(quantity)
                .status(MaterialAssignmentStatus.PENDING)
                .createdAt(Instant.now(clock))
                .build());
        return MaterialAssignmentView.of(saved);
    }

    /** Only the named recipient may accept — this is the moment the yarn actually lands on
     *  their own on-hand row. */
    public MaterialAssignmentView accept(String groupId, String userId, String assignmentId) {
        groupService.requireMember(groupId, userId);
        MaterialAssignment assignment = requirePendingAssignment(groupId, assignmentId);
        if (!assignment.getRecipientId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can accept this assignment");
        }
        MaterialAssignment resolved = transitionIfPending(groupId, assignmentId, MaterialAssignmentStatus.ACCEPTED);
        inventoryService.adjustQuantity(groupId, assignment.getRecipientId(), assignment.getYarnTypeId(), assignment.getQuantity());
        return MaterialAssignmentView.of(resolved);
    }

    /** Only the named recipient may reject — the quantity goes straight back to the
     *  proposer's own on-hand, as if it had never been sent. */
    public MaterialAssignmentView reject(String groupId, String userId, String assignmentId) {
        groupService.requireMember(groupId, userId);
        MaterialAssignment assignment = requirePendingAssignment(groupId, assignmentId);
        if (!assignment.getRecipientId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can reject this assignment");
        }
        MaterialAssignment resolved = transitionIfPending(groupId, assignmentId, MaterialAssignmentStatus.REJECTED);
        inventoryService.adjustQuantity(groupId, assignment.getProposerId(), assignment.getYarnTypeId(), assignment.getQuantity());
        return MaterialAssignmentView.of(resolved);
    }

    /** The proposer can withdraw their own still-pending assignment — same refund as a
     *  reject, since either way the yarn never actually reached the recipient. */
    public MaterialAssignmentView cancel(String groupId, String userId, String assignmentId) {
        groupService.requireMember(groupId, userId);
        MaterialAssignment assignment = requirePendingAssignment(groupId, assignmentId);
        if (!assignment.getProposerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the proposer can cancel this assignment");
        }
        MaterialAssignment resolved = transitionIfPending(groupId, assignmentId, MaterialAssignmentStatus.CANCELLED);
        inventoryService.adjustQuantity(groupId, assignment.getProposerId(), assignment.getYarnTypeId(), assignment.getQuantity());
        return MaterialAssignmentView.of(resolved);
    }

    /** Atomically flips {@code status} only if it's still {@code PENDING} — a plain
     *  conditional {@code findAndModify}, not a read-then-write, so two concurrent
     *  accept/reject/cancel calls on the same assignment can never both succeed and both
     *  move the inventory (same technique {@code TransferRequestService.reserveFulfillment}
     *  uses for its own atomic cap check). The caller has already checked identity/role
     *  against a fresh read before calling this; a {@code null} result here means someone
     *  else's call resolved it in the meantime. */
    private MaterialAssignment transitionIfPending(String groupId, String assignmentId, MaterialAssignmentStatus newStatus) {
        Query query = Query.query(Criteria.where("id").is(assignmentId).and("groupId").is(groupId)
                .and("status").is(MaterialAssignmentStatus.PENDING));
        Update update = new Update().set("status", newStatus).set("resolvedAt", Instant.now(clock));
        MaterialAssignment updated = mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), MaterialAssignment.class);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This assignment was just resolved elsewhere");
        }
        return updated;
    }

    private MaterialAssignment requirePendingAssignment(String groupId, String assignmentId) {
        MaterialAssignment assignment = ScopedLookup.requireInGroup(
                repository.findById(assignmentId), MaterialAssignment::getGroupId, groupId, "Assignment not found");
        if (assignment.getStatus() != MaterialAssignmentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This assignment is already " + assignment.getStatus());
        }
        return assignment;
    }
}
