package com.backlogtracker.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/users/me}. Optional — send it only if you're changing the
 * name. Email is not editable (it's the login id).
 */
public record UpdateProfileRequest(@Size(max = 80) String name) {
}
