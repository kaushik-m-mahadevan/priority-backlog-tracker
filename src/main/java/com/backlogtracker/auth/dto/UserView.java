package com.backlogtracker.auth.dto;

import com.backlogtracker.security.AuthUser;
import com.backlogtracker.user.domain.User;

/** Safe user projection for API responses — never carries the password hash. */
public record UserView(String id, String name, String email, String role) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

    public static UserView of(AuthUser user) {
        return new UserView(user.id(), user.name(), user.email(), user.role().name());
    }
}
