package com.backlogtracker.notification.domain;

public enum NotificationType {
    /** Actionable: accept joins the group. */
    GROUP_INVITE,
    /** Actionable: approve / reject a request to archive an active item. */
    ARCHIVE_REQUEST,
    /** Informational: an archive request you were part of was resolved. */
    ARCHIVE_RESULT,
    /** Informational: an admin acted on your password request. */
    PASSWORD_RESULT
}
