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
    /** ARCHIVE_REQUEST: the requester's optional note. ARCHIVE_RESULT: the outcome text. */
    private String message;
}
