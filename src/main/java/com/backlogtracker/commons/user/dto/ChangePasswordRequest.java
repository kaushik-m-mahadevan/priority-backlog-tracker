package com.backlogtracker.commons.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/auth/password-change} — routed to an admin for approval.
 *  {@code replaceExisting} (ad-7): when the requester already has a pending request, the
 *  first call without this flag is rejected with 409 so the client can offer "replace
 *  pending request?"; resubmitting with it true supersedes the old one. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "New password must be at least 8 characters")
        String newPassword,
        boolean replaceExisting) {
}
