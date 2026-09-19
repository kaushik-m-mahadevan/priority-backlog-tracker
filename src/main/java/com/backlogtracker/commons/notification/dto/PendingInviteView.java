package com.backlogtracker.commons.notification.dto;

import java.time.Instant;

/** One outstanding invite on a group's "Pending invites" list (view-only for now — no
 *  cancel/revoke action yet). {@code invitedDisplay} is the invitee's @handle if they have
 *  one, else their email, matching how they'd actually recognize themselves. */
public record PendingInviteView(String id, String invitedDisplay, String invitedByName, Instant createdAt) {
}
