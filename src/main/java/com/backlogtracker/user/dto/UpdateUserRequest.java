package com.backlogtracker.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Patch a team member (Owner-only). Both fields optional: send {@code role} to change it,
 * {@code password} to reset it. {@code @Size} only fires when a password is supplied.
 */
public record UpdateUserRequest(
        String role,
        @Size(min = 8, message = "Password must be at least 8 characters") String password) {
}
