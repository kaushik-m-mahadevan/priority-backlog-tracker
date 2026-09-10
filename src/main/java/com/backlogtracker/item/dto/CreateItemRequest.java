package com.backlogtracker.item.dto;

import java.time.Instant;

import com.backlogtracker.item.domain.EffortEstimate;

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
        String notes) {
}
