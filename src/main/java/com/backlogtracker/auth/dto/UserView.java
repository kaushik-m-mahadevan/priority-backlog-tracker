package com.backlogtracker.auth.dto;

import com.backlogtracker.security.AuthUser;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.User;

/** Safe user projection for API responses — never carries the password hash. */
public record UserView(String id, String name, String email, String role, String status,
                       boolean animationsEnabled) {

    public static UserView of(User user) {
        AccountStatus status = user.getStatus() == null ? AccountStatus.ACTIVE : user.getStatus();
        return new UserView(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), status.name(), user.isAnimationsEnabled());
    }

    public static UserView of(AuthUser user) {
        return new UserView(user.id(), user.name(), user.email(),
                user.role().name(), user.status().name(), false);
    }
}
