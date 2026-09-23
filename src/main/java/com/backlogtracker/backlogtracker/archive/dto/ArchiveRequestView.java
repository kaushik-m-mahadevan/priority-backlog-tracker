package com.backlogtracker.backlogtracker.archive.dto;

import java.time.Instant;
import java.util.Map;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;

/**
 * The JSON shape the frontend has always seen for an archive request, now built from the
 * generic {@link ApprovalRequest} (qd-1) instead of a bespoke domain document — field
 * names are kept identical on purpose so the frontend needed no changes. {@code itemId},
 * {@code itemTitle}, {@code requestedByName} and {@code note} live in the approval's
 * opaque payload; {@code decidedAt} is {@link ApprovalRequest#getResolvedAt()} under its
 * old, more archive-specific name.
 */
public record ArchiveRequestView(
        String id,
        String itemId,
        String itemTitle,
        String groupId,
        String requestedByName,
        String note,
        String status,
        int approvedCount,
        Instant createdAt,
        Instant decidedAt) {

    public static ArchiveRequestView of(ApprovalRequest r) {
        Map<String, Object> p = r.getPayload();
        return new ArchiveRequestView(
                r.getId(), (String) p.get("itemId"), (String) p.get("itemTitle"), r.getGroupId(),
                (String) p.get("requestedByName"), (String) p.get("note"),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getApprovedByUserIds() == null ? 0 : r.getApprovedByUserIds().size(),
                r.getCreatedAt(), r.getResolvedAt());
    }
}
