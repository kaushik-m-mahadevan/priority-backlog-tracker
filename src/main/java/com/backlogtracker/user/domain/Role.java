package com.backlogtracker.user.domain;

/**
 * User roles (design §8). Currently every account is provisioned as {@link #OWNER};
 * CONTRIBUTOR and VIEWER exist so access checks can be written against them now and
 * enforced when those accounts are introduced.
 */
public enum Role {
    OWNER,
    CONTRIBUTOR,
    VIEWER;

    /** Spring Security authority name, e.g. {@code ROLE_OWNER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
