package com.backlogtracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration. Produces a {@code USER} account in {@code PENDING} state — an admin
 * approves it before any feature is usable.
 */
public record RegisterRequest(
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "^[a-z0-9_-]{1,30}$",
                message = "Handle must be 1-30 characters: lowercase letters, digits, - or _")
        String handle,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password) {
}
