package com.backlogtracker.commons.notification.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A per-user inbox entry. Built generic; {@link NotificationType#GROUP_INVITE} is the
 * first (and, for now, only) type — accepting it joins the group.
 */
@Document("notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    /** Recipient. */
    @Indexed
    private String userId;

    private NotificationType type;
    private NotificationStatus status;

    /** Whether this notice needs the recipient to actually do something about it — the
     *  single flag every applet's notifications are judged by for the bell's red badge
     *  count, replacing the old hardcoded per-type allowlist that new types kept missing
     *  (GROUP_LINK_PROPOSED shipped "actionable" in its own doc comment but was never added
     *  to that list, so it never counted). {@code true} only while {@code status == PENDING}
     *  is still meaningful for this notice — informational notices stay {@code false} even
     *  though they're saved with status ACCEPTED (see {@link NotificationStatus}). */
    private boolean actionable;

    /** The header every notification renders under, uniformly, regardless of applet —
     *  the short "what kind of thing is this" label (e.g. "Order finalization",
     *  "Yarn request"). {@code message} below is the body text. */
    private String title;

    /** Where clicking this notification should take the recipient — an app-relative route,
     *  or {@code null} when there's nowhere more specific to send them than the inbox itself. */
    private String linkPath;

    /** Generic id used to find and auto-resolve this notice later, once the thing it's
     *  about (a material assignment, a transfer request, ...) is settled elsewhere — the
     *  same shape {@code archiveRequestId} already served for archive votes, generalized so
     *  new actionable types don't each need their own dedicated id column. */
    private String referenceId;

    @CreatedDate
    private Instant createdAt;
    private Instant actedAt;

    // ---- GROUP_INVITE payload ----
    private String groupId;
    private String groupName;
    private String invitedByUserId;
    private String invitedByName;

    // ---- ARCHIVE_REQUEST / ARCHIVE_RESULT payload ----
    private String archiveRequestId;
    private String itemId;
    private String itemTitle;
    /** ARCHIVE_REQUEST: the requester's optional note. ARCHIVE_RESULT: the outcome text.
     *  Every other type: the notification's body text, shown under {@link #title}. */
    private String message;
}
