package com.backlogtracker.item.dto;

import java.time.Instant;

import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.domain.Notes;

/** Client-facing projection of an {@link Item}. */
public record ItemView(
        String id,
        String itemId,
        String title,
        String category,
        String priority,
        EffortView effort,
        Instant dueDate,
        String status,
        String groupId,
        String ownerId,
        String createdBy,
        String lastUpdatedBy,
        Notes notes,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public record EffortView(int value, String unit, long minutes) {
    }

    public static ItemView of(Item i) {
        EffortView effort = i.getEffortEstimate() == null ? null
                : new EffortView(
                        i.getEffortEstimate().getValue(),
                        i.getEffortEstimate().getUnit().name(),
                        i.getEffortEstimate().toMinutes());
        return new ItemView(
                i.getId(), i.getItemId(), i.getTitle(), i.getCategory(), i.getPriority(),
                effort, i.getDueDate(),
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getGroupId(),
                i.getOwnerId(), i.getCreatedBy(), i.getLastUpdatedBy(), i.getNotes(),
                i.getCreatedAt(), i.getUpdatedAt(), i.getVersion());
    }
}
