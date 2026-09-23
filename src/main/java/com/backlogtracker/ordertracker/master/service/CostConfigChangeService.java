package com.backlogtracker.ordertracker.master.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.ordertracker.master.dto.CostConfigChangeRequestView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the generic {@link ApprovalService} to a business's overhead %/profit margin %
 * change proposals (qd-1) — every other {@code BusinessConfig} field stays freely editable
 * by any member; only these, since they directly change every order's price. One group has
 * at most one PENDING cost-config change at a time, {@code kind = "ordertracker:cost-config"}
 * (the exact example {@link ApprovalRequest}'s own javadoc anticipated for this migration).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostConfigChangeService {

    private static final String KIND = "ordertracker:cost-config";

    private final ApprovalService approvalService;
    private final GroupService groupService;
    private final BusinessConfigService businessConfigService;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;

    public CostConfigChangeRequestView propose(String groupId, String userId,
                                               double overheadPercentage, double profitMarginPercentage,
                                               double hourlyWage) {
        Group group = groupService.requireMember(groupId, userId);
        ApprovalRequest approval = approvalService.propose(groupId, userId, KIND, Map.of(
                "overheadPercentage", overheadPercentage,
                "profitMarginPercentage", profitMarginPercentage,
                "hourlyWage", hourlyWage));
        applyIfResolved(approval);
        if (approval.getStatus() == ApprovalStatus.PENDING) {
            notificationOrchestrator.notifyOtherMembers(group, userId, NotificationType.COST_CONFIG_PROPOSED,
                    "A new overhead/profit-margin proposal is waiting for your approval.");
        }
        return CostConfigChangeRequestView.of(approval);
    }

    public CostConfigChangeRequestView approve(String groupId, String userId, String requestId) {
        ApprovalRequest approval = approvalService.approve(groupId, userId, requestId);
        applyIfResolved(approval);
        return CostConfigChangeRequestView.of(approval);
    }

    public CostConfigChangeRequestView reject(String groupId, String userId, String requestId) {
        ApprovalRequest approval = approvalService.reject(groupId, userId, requestId);
        return CostConfigChangeRequestView.of(approval);
    }

    public List<CostConfigChangeRequestView> list(String groupId, String userId) {
        return approvalService.list(groupId, userId, KIND).stream()
                .map(CostConfigChangeRequestView::of)
                .toList();
    }

    /** The generic approval layer only announces an invalidation — sending the actual
     *  notification is a per-kind concern, mirroring OrderFinalizationService's and
     *  ProfitDistributionService's own listeners for the same event. */
    @EventListener
    public void onApprovalInvalidated(ApprovalRequestInvalidatedEvent event) {
        if (!event.kind().equals(KIND)) {
            return;
        }
        notificationService.info(event.proposedByUserId(), NotificationType.COST_CONFIG_INVALIDATED,
                "Your proposed overhead/profit-margin change was cancelled because a member left "
                        + "the group mid-approval. You can propose it again.");
        log.info("Cost-config request {} invalidated", event.requestId());
    }

    /** Only ever reached once per resolution: {@link ApprovalService#approve} already
     *  guarantees exactly-once resolution under retry (a second approve() call on an
     *  already-resolved request 409s before this is reached), and propose()'s solo-proposer
     *  auto-resolve only ever returns APPROVED once too. */
    private void applyIfResolved(ApprovalRequest approval) {
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            return;
        }
        Map<String, Object> p = approval.getPayload();
        businessConfigService.applyCostConfig(approval.getGroupId(),
                ((Number) p.get("overheadPercentage")).doubleValue(),
                ((Number) p.get("profitMarginPercentage")).doubleValue(),
                ((Number) p.get("hourlyWage")).doubleValue());
    }
}
