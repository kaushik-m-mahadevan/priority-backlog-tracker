package com.backlogtracker.user.domain;

/**
 * Where an account is in the onboarding lifecycle. A {@link #PENDING} account can
 * authenticate but every endpoint except {@code GET /api/auth/me} returns 403 until an
 * admin approves it. Legacy documents with no status are treated as {@link #ACTIVE}.
 */
public enum AccountStatus {
    PENDING,
    ACTIVE
}
