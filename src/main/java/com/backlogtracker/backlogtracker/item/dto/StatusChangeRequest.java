package com.backlogtracker.backlogtracker.item.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code status} must be {@code BACKLOG} or {@code IN_PROGRESS} (design §4). */
public record StatusChangeRequest(@NotBlank String status) {
}
