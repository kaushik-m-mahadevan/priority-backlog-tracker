package com.backlogtracker.commons.approval.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.event.MemberLeftGroupEvent;
import com.backlogtracker.commons.group.service.GroupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic unanimous-approval workflow, extracted from Order Tracker's own
 * {@code CostConfigChangeService} so that feature (cost-config changes) and any later ones
 * needing the identical "every current group member must agree, a member leaving mid-vote
 * cancels it" mechanics (order-finalization, profit-split — per design decision) don't each
 * reimplement the state machine.
 *
 * <p>This layer only runs the approval mechanics. It never interprets {@code payload} and
 * never applies a domain-specific side effect itself — callers read the returned
 * {@link ApprovalRequest#getStatus()} after {@link #propose} / {@link #approve} and apply
 * whatever should happen on {@link ApprovalStatus#APPROVED} themselves, exactly once, only
 * when the call that resolved it is the one that told them so (never by separately polling
 * status), so a retried-but-ultimately-successful save can't double-apply anything.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    // Was 3 (this service's original cap, inherited from CostConfigChangeService's own
    // handful-of-members contention case) until qd-1 migrated ArchiveRequestService onto
    // this class — that caller's own regression test races 5 concurrent approvers against
    // one request, which can legitimately need more than 3 retries in the worst case under
    // heavy contention (archive's old bespoke retry cap was 20, specifically for this same
    // reason). Bumped for every caller, not just archive's: more retries only costs a little
    // worst-case latency under real contention, never incorrectness, so there's no reason to
    // keep the other callers on a tighter cap.
    private static final int MAX_APPROVE_RETRIES = 10;

    private final ApprovalRequestRepository repository;
    private final GroupService groupService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalRequest propose(String groupId, String userId, String kind, Map<String, Object> payload) {
        Group group = groupService.requireMember(groupId, userId);
        repository.findByGroupIdAndKindAndStatus(groupId, kind, ApprovalStatus.PENDING).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An approval of this kind is already pending for this group");
        });
        ApprovalRequest request = ApprovalRequest.builder()
                .groupId(groupId)
                .kind(kind)
                .payload(payload)
                .proposedByUserId(userId)
                .approvedByUserIds(new ArrayList<>(List.of(userId)))
                .status(ApprovalStatus.PENDING)
                .createdAt(Instant.now(clock))
                .build();
        markResolvedIfUnanimous(request, group);
        return repository.save(request);
    }

    /** Retries on a lost-update race (two members approving at once) by re-reading the
     *  latest approvedByUserIds and reapplying this approval on top of it, mirroring
     *  CostConfigChangeService.approve()'s retry loop. */
    public ApprovalRequest approve(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        for (int attempt = 1; attempt <= MAX_APPROVE_RETRIES; attempt++) {
            ApprovalRequest request = pendingRequest(groupId, requestId);
            if (!request.getApprovedByUserIds().contains(userId)) {
                request.getApprovedByUserIds().add(userId);
            }
            markResolvedIfUnanimous(request, group);
            try {
                return repository.save(request);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_APPROVE_RETRIES) {
                    throw e;
                }
                log.info("Approval race on request {} — retrying (attempt {})", requestId, attempt);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** A lone proposer in a single-member group already satisfies unanimity the moment they
     *  propose, mirroring CostConfigChangeService's own solo-proposer auto-resolution. */
    private void markResolvedIfUnanimous(ApprovalRequest request, Group group) {
        if (request.getApprovedByUserIds().containsAll(group.getMemberIds())) {
            request.setStatus(ApprovalStatus.APPROVED);
            request.setResolvedAt(Instant.now(clock));
        }
    }

    public ApprovalRequest reject(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);
        ApprovalRequest request = pendingRequest(groupId, requestId);
        request.setStatus(ApprovalStatus.REJECTED);
        request.setRejectedByUserId(userId);
        request.setResolvedAt(Instant.now(clock));
        return repository.save(request);
    }

    public List<ApprovalRequest> list(String groupId, String userId, String kind) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupIdAndKind(groupId, kind);
    }

    /** A member leaving mid-approval can reasonably change how the remaining members would
     *  have voted, so every pending proposal in that group is cancelled outright rather than
     *  silently resolved on whoever's left — mirrors CostConfigChangeService.onMemberLeft(),
     *  generalized across every `kind` since a group can have concurrent approval workflows
     *  of different kinds. */
    @EventListener
    public void onMemberLeft(MemberLeftGroupEvent event) {
        repository.findByGroupIdAndStatus(event.groupId(), ApprovalStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(ApprovalStatus.INVALIDATED);
                    request.setResolvedAt(Instant.now(clock));
                    repository.save(request);
                    events.publishEvent(new ApprovalRequestInvalidatedEvent(
                            request.getId(), request.getGroupId(), request.getKind(), request.getProposedByUserId()));
                    log.info("Approval request {} (kind {}) invalidated — member {} left group {}",
                            request.getId(), request.getKind(), event.userId(), event.groupId());
                });
    }

    private ApprovalRequest pendingRequest(String groupId, String requestId) {
        ApprovalRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (!request.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This approval request has already been resolved");
        }
        return request;
    }
}
