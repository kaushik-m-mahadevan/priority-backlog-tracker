package com.backlogtracker.commons.approval.event;

/**
 * Published whenever a pending {@code ApprovalRequest} is invalidated by a member leaving
 * the group mid-vote. The generic approval layer only announces this — it deliberately
 * doesn't know how to notify anyone about it, since the right message text is a
 * per-{@code kind} concern; a {@code kind}-specific listener (e.g. Finance Tracker's
 * profit-split feature) reacts to this and sends its own notification.
 */
public record ApprovalRequestInvalidatedEvent(String requestId, String groupId, String kind,
                                               String proposedByUserId) {
}
