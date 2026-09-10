package com.backlogtracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/auth/forgot-password}. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
