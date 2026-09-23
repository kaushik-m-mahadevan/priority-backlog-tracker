package com.backlogtracker.ordertracker.master.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;

/**
 * The JSON shape the frontend has always seen for a cost-config change request, now built
 * from the generic {@link ApprovalRequest} (qd-1) instead of a bespoke domain document —
 * the field names are kept identical on purpose so the frontend needed no changes.
 */
public record CostConfigChangeRequestView(
        String id,
        String groupId,
        double proposedOverheadPercentage,
        double proposedProfitMarginPercentage,
        double proposedHourlyWage,
        String proposedByUserId,
        List<String> approvedByUserIds,
        ApprovalStatus status,
        String rejectedByUserId,
        Instant createdAt,
        Instant resolvedAt) {

    public static CostConfigChangeRequestView of(ApprovalRequest r) {
        Map<String, Object> p = r.getPayload();
        return new CostConfigChangeRequestView(
                r.getId(),
                r.getGroupId(),
                ((Number) p.get("overheadPercentage")).doubleValue(),
                ((Number) p.get("profitMarginPercentage")).doubleValue(),
                ((Number) p.get("hourlyWage")).doubleValue(),
                r.getProposedByUserId(),
                r.getApprovedByUserIds(),
                r.getStatus(),
                r.getRejectedByUserId(),
                r.getCreatedAt(),
                r.getResolvedAt());
    }
}
