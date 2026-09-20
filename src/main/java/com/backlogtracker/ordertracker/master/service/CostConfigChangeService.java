package com.backlogtracker.ordertracker.master.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.event.MemberLeftGroupEvent;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeRequest;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeStatus;
import com.backlogtracker.ordertracker.master.repository.CostConfigChangeRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Proposes and resolves changes to a business's overhead %/profit margin %. Every current
 * group member must approve before the change takes effect; a single rejection cancels the
 * whole proposal (mirrors Backlog Tracker's own unanimous archive-approval pattern, kept as
 * a separate implementation per the platform's no-cross-applet-imports rule).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostConfigChangeService {

    private static final int MAX_APPROVE_RETRIES = 3;

    private final CostConfigChangeRequestRepository repository;
    private final GroupService groupService;
    private final BusinessConfigService businessConfigService;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final Clock clock;

    public CostConfigChangeRequest propose(String groupId, String userId,
                                           double overheadPercentage, double profitMarginPercentage,
                                           double hourlyWage) {
        Group group = groupService.requireMember(groupId, userId);
        repository.findByGroupIdAndStatus(groupId, CostConfigChangeStatus.PENDING).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cost config change is already pending approval for this group");
        });
        CostConfigChangeRequest request = CostConfigChangeRequest.builder()
                .groupId(groupId)
                .proposedOverheadPercentage(overheadPercentage)
                .proposedProfitMarginPercentage(profitMarginPercentage)
                .proposedHourlyWage(hourlyWage)
                .proposedByUserId(userId)
                .status(CostConfigChangeStatus.PENDING)
                .createdAt(Instant.now(clock))
                .build();
        request.getApprovedByUserIds().add(userId);
        boolean unanimous = markResolvedIfUnanimous(request, group);
        CostConfigChangeRequest saved = repository.save(request);
        if (unanimous) {
            applyResolvedConfig(saved);
        } else {
            notificationOrchestrator.notifyOtherMembers(group, userId, NotificationType.COST_CONFIG_PROPOSED,
                    "A new overhead/profit-margin proposal is waiting for your approval.");
        }
        return saved;
    }

    /** Retries on a lost-update race (two members approving the same request at once) by
     *  re-reading the latest approvedByUserIds and reapplying this approval on top of it,
     *  rather than silently losing whichever save lost the race. The config-apply side
     *  effect only runs after a save actually succeeds — otherwise a losing attempt that
     *  gets retried would apply the config once per retry instead of once overall. */
    public CostConfigChangeRequest approve(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        for (int attempt = 1; attempt <= MAX_APPROVE_RETRIES; attempt++) {
            CostConfigChangeRequest request = pendingRequest(groupId, requestId);
            if (!request.getApprovedByUserIds().contains(userId)) {
                request.getApprovedByUserIds().add(userId);
            }
            boolean unanimous = markResolvedIfUnanimous(request, group);
            try {
                CostConfigChangeRequest saved = repository.save(request);
                if (unanimous) {
                    applyResolvedConfig(saved);
                }
                return saved;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_APPROVE_RETRIES) {
                    throw e;
                }
                log.info("Cost-config approve race on request {} — retrying (attempt {})", requestId, attempt);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** A lone proposer in a single-member group already satisfies unanimity the moment they
     *  propose — checked here (not just in approve()) so a solo business isn't left waiting
     *  on an "approval" nobody else can ever give. Only marks the request's own fields —
     *  callers apply the actual config-change side effect themselves, after a successful
     *  save, so a retried-but-ultimately-successful save can't apply it twice. */
    private boolean markResolvedIfUnanimous(CostConfigChangeRequest request, Group group) {
        if (request.getApprovedByUserIds().containsAll(group.getMemberIds())) {
            request.setStatus(CostConfigChangeStatus.APPROVED);
            request.setResolvedAt(Instant.now(clock));
            return true;
        }
        return false;
    }

    private void applyResolvedConfig(CostConfigChangeRequest request) {
        businessConfigService.applyCostConfig(request.getGroupId(), request.getProposedOverheadPercentage(),
                request.getProposedProfitMarginPercentage(), request.getProposedHourlyWage());
    }

    public CostConfigChangeRequest reject(String groupId, String userId, String requestId) {
        groupService.requireMember(groupId, userId);
        CostConfigChangeRequest request = pendingRequest(groupId, requestId);
        request.setStatus(CostConfigChangeStatus.REJECTED);
        request.setRejectedByUserId(userId);
        request.setResolvedAt(Instant.now(clock));
        return repository.save(request);
    }

    public List<CostConfigChangeRequest> list(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId);
    }

    /** A member leaving mid-approval can reasonably change how the remaining members would
     *  have voted, so the pending proposal is cancelled outright rather than silently
     *  resolved on whoever's left — the proposer is notified and can simply propose again
     *  (propose() only refuses while one is still PENDING, so nothing else to "retrigger"). */
    @EventListener
    public void onMemberLeft(MemberLeftGroupEvent event) {
        repository.findByGroupIdAndStatus(event.groupId(), CostConfigChangeStatus.PENDING)
                .ifPresent(request -> {
                    request.setStatus(CostConfigChangeStatus.INVALIDATED);
                    request.setResolvedAt(Instant.now(clock));
                    repository.save(request);
                    notificationService.info(request.getProposedByUserId(), NotificationType.COST_CONFIG_INVALIDATED,
                            "Your proposed overhead/profit-margin change was cancelled because a member left "
                                    + "the group mid-approval. You can propose it again.");
                    log.info("Cost-config request {} invalidated — member {} left group {}",
                            request.getId(), event.userId(), event.groupId());
                });
    }

    private CostConfigChangeRequest pendingRequest(String groupId, String requestId) {
        CostConfigChangeRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change request not found"));
        if (!request.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Change request not found");
        }
        if (request.getStatus() != CostConfigChangeStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This change request has already been resolved");
        }
        return request;
    }
}
