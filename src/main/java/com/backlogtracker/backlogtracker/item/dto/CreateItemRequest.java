package com.backlogtracker.backlogtracker.item.dto;

import java.time.Instant;

import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create a shared backlog item. {@code dueDate} and {@code ownerId} are optional;
 * a missing due date defaults to {@code now + config.defaultDueDateOffsetDays} (§2).
 */
public record CreateItemRequest(
        @NotBlank String groupId,
        @NotBlank String title,
        @NotBlank String category,
        @NotBlank String priority,
        @NotNull @Valid EffortEstimate effortEstimate,
        Instant dueDate,
        String ownerId,
        String notes,
        /** Opaque Order Tracker order id (platform integration follow-up) — optional, only
         *  ever set by the "add to group" flow; never rendered anywhere in this applet's UI. */
        String linkedOrderId) {
}
