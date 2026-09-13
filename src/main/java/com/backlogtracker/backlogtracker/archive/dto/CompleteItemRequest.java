package com.backlogtracker.backlogtracker.archive.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code terminalStatus} must be RESOLVED, REJECTED, or ARCHIVED (design §24). */
public record CompleteItemRequest(@NotBlank String terminalStatus) {
}
