package com.backlogtracker.commons.notification.dto;

import java.time.Instant;

import com.backlogtracker.commons.notification.domain.Notification;

public record NotificationView(String id, String type, String status, Instant createdAt,
                               String groupId, String groupName, String groupAppletKey,
                               String invitedByName,
                               String archiveRequestId, String itemId, String itemTitle,
                               String message) {

    /** mb-2: {@code groupAppletKey} lets the frontend show which applet a notification's
     *  group belongs to — two groups from different applets can otherwise share a name
     *  (e.g. a Backlog Tracker group and a business both called "Founders") and look
     *  identical in the inbox. {@code null} when the notification carries no groupId, or
     *  the caller didn't have that group's appletKey handy (e.g. it's since been deleted). */
    public static NotificationView of(Notification n, String groupAppletKey) {
        return new NotificationView(n.getId(), n.getType().name(), n.getStatus().name(),
                n.getCreatedAt(), n.getGroupId(), n.getGroupName(), groupAppletKey, n.getInvitedByName(),
                n.getArchiveRequestId(), n.getItemId(), n.getItemTitle(), n.getMessage());
    }
}
