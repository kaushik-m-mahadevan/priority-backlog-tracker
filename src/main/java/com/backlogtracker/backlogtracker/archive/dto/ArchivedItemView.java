package com.backlogtracker.backlogtracker.archive.dto;

import java.time.Instant;

import com.backlogtracker.backlogtracker.archive.domain.ArchivedItem;
import com.backlogtracker.backlogtracker.item.dto.ItemView.EffortView;

public record ArchivedItemView(
        String id,
        String itemId,
        String title,
        String category,
        String priority,
        EffortView effort,
        Instant dueDate,
        String groupId,
        String ownerId,
        String createdBy,
        String lastUpdatedBy,
        String terminalStatus,
        Instant completionDate,
        Instant movedAt,
        Instant createdAt) {

    public static ArchivedItemView of(ArchivedItem a) {
        EffortView effort = a.getEffortEstimate() == null ? null
                : new EffortView(a.getEffortEstimate().getValue(),
                a.getEffortEstimate().getUnit().name(),
                a.getEffortEstimate().toMinutes());
        return new ArchivedItemView(
                a.getId(), a.getItemId(), a.getTitle(), a.getCategory(), a.getPriority(),
                effort, a.getDueDate(),
                a.getGroupId(),
                a.getOwnerId(), a.getCreatedBy(), a.getLastUpdatedBy(),
                a.getTerminalStatus() == null ? null : a.getTerminalStatus().name(),
                a.getCompletionDate(), a.getMovedAt(), a.getCreatedAt());
    }
}
