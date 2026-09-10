package com.backlogtracker.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** Invite someone to a group by their email or their @handle. */
public record InviteRequest(@NotBlank String to) {
}
