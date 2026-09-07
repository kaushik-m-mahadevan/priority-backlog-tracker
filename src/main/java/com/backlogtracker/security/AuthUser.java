package com.backlogtracker.security;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;

/**
 * The authenticated principal placed in the security context and injectable into
 * controllers via {@code @AuthenticationPrincipal AuthUser}.
 */
public record AuthUser(String id, String email, String name, Role role) {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
