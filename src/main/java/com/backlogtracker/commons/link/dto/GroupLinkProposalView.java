package com.backlogtracker.commons.link.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;

/** mb-14: a proposal to link {@code groupId} to {@code targetGroupId}, gated behind the
 *  same unanimous-approval mechanics as a cost-config change or order finalization — built
 *  from the generic {@link ApprovalRequest}, same convention as
 *  {@code CostConfigChangeRequestView}/{@code ProfitDistributionView}. */
public record GroupLinkProposalView(
        String id,
        String groupId,
        String targetGroupId,
        List<String> intoCurrentEmails,
        List<String> intoTargetEmails,
        String proposedByUserId,
        List<String> approvedByUserIds,
        List<String> groupMemberIds,
        ApprovalStatus status,
        String rejectedByUserId,
        Instant createdAt,
        Instant resolvedAt) {

    @SuppressWarnings("unchecked")
    public static GroupLinkProposalView of(ApprovalRequest r, List<String> groupMemberIds) {
        Map<String, Object> p = r.getPayload();
        return new GroupLinkProposalView(
                r.getId(),
                r.getGroupId(),
                (String) p.get("groupIdB"),
                (List<String>) p.getOrDefault("intoCurrentEmails", List.of()),
                (List<String>) p.getOrDefault("intoTargetEmails", List.of()),
                r.getProposedByUserId(),
                r.getApprovedByUserIds(),
                groupMemberIds,
                r.getStatus(),
                r.getRejectedByUserId(),
                r.getCreatedAt(),
                r.getResolvedAt());
    }
}
