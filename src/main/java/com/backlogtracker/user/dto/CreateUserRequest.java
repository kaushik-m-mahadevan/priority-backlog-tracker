package com.backlogtracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create a team member (Owner-only). {@code role} defaults to OWNER (design §8 — everyone
 * is an Owner for now); {@code userCode} is derived from the name when blank.
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        String role) {
}
