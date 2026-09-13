package com.backlogtracker.commons.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/auth/forgot-password}. Not {@code @Email}-validated — legacy
 * accounts may have a non-email login id (matches the login screen).
 */
public record ForgotPasswordRequest(@NotBlank String email) {
}
