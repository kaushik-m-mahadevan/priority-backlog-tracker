package com.backlogtracker.notification.domain;

/** For actionable types. Non-actionable notifications (later) will use readAt instead. */
public enum NotificationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
