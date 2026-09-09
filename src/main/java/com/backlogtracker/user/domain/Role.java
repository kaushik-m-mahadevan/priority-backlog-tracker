package com.backlogtracker.user.domain;

/**
 * Account roles. There are only two: {@link #ADMIN} (approves onboarding, owns the
 * ranking/formula settings) and {@link #USER} (everything else, scoped to their groups).
 * ADMIN has no extra power inside a group.
 */
public enum Role {
    ADMIN,
    USER;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
