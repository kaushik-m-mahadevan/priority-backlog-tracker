package com.backlogtracker.security;

import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;

/**
 * The authenticated principal placed in the security context and injectable into
 * controllers via {@code @AuthenticationPrincipal AuthUser}. Rebuilt from the database
 * on every request, so role and status are always current.
 */
public record AuthUser(String id, String email, String name, Role role, AccountStatus status) {

    public static AuthUser from(User user) {
        AccountStatus status = user.getStatus() == null ? AccountStatus.ACTIVE : user.getStatus();
        return new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole(), status);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
