package com.backlogtracker.item.dto;

import java.time.Instant;

import com.backlogtracker.item.domain.EffortEstimate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Full update of an item's editable fields. Status changes go through
 * {@code PATCH /api/items/{id}/status} instead (design §4).
 */
public record UpdateItemRequest(
        @NotBlank String title,
        @NotBlank String category,
        @NotBlank String priority,
        @NotNull @Valid EffortEstimate effortEstimate,
        @NotNull Instant dueDate,
        String ownerId,
        /** Markdown notes. null = leave unchanged; "" = clear. */
        String notes,
        /** The version the client last saw; a stale value is rejected with 409 (§19). */
        Long version) {
}
