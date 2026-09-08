package com.backlogtracker.archive.domain;

/**
 * The three ways an item leaves the live table (design §4, §24). Reaching any of these
 * physically moves the document from {@code items} to {@code archivedItems}.
 */
public enum TerminalStatus {
    RESOLVED,
    REJECTED,
    ARCHIVED
}
