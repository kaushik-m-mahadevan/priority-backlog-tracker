package com.backlogtracker.backlogtracker.item.domain;

/**
 * Live statuses only (design §4, §24). Terminal states (Resolved / Rejected / Archived)
 * are not values here — reaching one physically moves the document to
 * {@code archivedItems}.
 */
public enum ItemStatus {
    BACKLOG,
    IN_PROGRESS
}
