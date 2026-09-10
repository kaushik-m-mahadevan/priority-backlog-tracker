package com.backlogtracker.archive.dto;

import java.time.Instant;

import com.backlogtracker.archive.domain.ArchiveRequest;

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

    public static ArchiveRequestView of(ArchiveRequest r) {
        return new ArchiveRequestView(
                r.getId(), r.getItemId(), r.getItemTitle(), r.getGroupId(),
                r.getRequestedByName(), r.getNote(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getApprovedByUserIds() == null ? 0 : r.getApprovedByUserIds().size(),
                r.getCreatedAt(), r.getDecidedAt());
    }
}
