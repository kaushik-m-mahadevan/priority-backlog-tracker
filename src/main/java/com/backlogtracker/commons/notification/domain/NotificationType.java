package com.backlogtracker.commons.notification.domain;

public enum NotificationType {
    /** Actionable: accept joins the group. */
    GROUP_INVITE,
    /** Actionable: approve / reject a request to archive an active item. */
    ARCHIVE_REQUEST,
    /** Informational: an archive request you were part of was resolved. */
    ARCHIVE_RESULT,
    /** Informational: an admin acted on your password request. */
    PASSWORD_RESULT,
    /** Informational: your pending overhead/profit-margin proposal was auto-cancelled
     *  because a member left the group mid-approval — you can re-propose. */
    COST_CONFIG_INVALIDATED,
    /** Informational: your pending order-finalization proposal was auto-cancelled because
     *  a member left the group mid-approval — you can re-propose. */
    ORDER_FINALIZATION_INVALIDATED,
    /** Informational: your pending profit-distribution proposal was auto-cancelled because
     *  a member left the group mid-approval — you can re-propose. */
    PROFIT_DISTRIBUTION_INVALIDATED,
    /** Informational: your pending archive request was auto-cancelled because a member
     *  left the group mid-approval — you can re-raise it. */
    ARCHIVE_REQUEST_INVALIDATED,

    // ad-4: proactive "something new needs your attention" notices — previously these 5
    // events (proposal creation, signup, password request) left the other party to
    // discover them only by manually checking a list/admin page.
    /** Informational: another group member proposed a new overhead/profit-margin rate. */
    COST_CONFIG_PROPOSED,
    /** Informational: another group member proposed an order's final cost/revenue. */
    ORDER_FINALIZATION_PROPOSED,
    /** Informational (mb-12): an order's finalization proposal just reached unanimous
     *  approval and is now locked in — previously nothing told anyone this happened; they
     *  had to open the order and check manually. */
    ORDER_FINALIZATION_RESOLVED,
    /** Informational: another group member proposed a profit distribution. */
    PROFIT_DISTRIBUTION_PROPOSED,
    /** Informational (admins only): a new account is waiting for approval. */
    SIGNUP_PENDING,
    /** Informational (admins only): a password change/reset request is waiting. */
    PASSWORD_REQUEST_PENDING
}
