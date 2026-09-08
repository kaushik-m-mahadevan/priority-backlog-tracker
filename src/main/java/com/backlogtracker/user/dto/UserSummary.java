package com.backlogtracker.user.dto;

import com.backlogtracker.user.domain.User;

/** Minimal user projection for pickers and owner labels. Never carries the hash. */
public record UserSummary(String id, String name, String email, String role) {

    public static UserSummary of(User u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }
}
