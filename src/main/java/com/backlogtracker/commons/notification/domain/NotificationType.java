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
    ORDER_FINALIZATION_INVALIDATED
}
