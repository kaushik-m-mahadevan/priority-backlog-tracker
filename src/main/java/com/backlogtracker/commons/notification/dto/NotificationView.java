package com.backlogtracker.commons.notification.dto;

import java.time.Instant;

import com.backlogtracker.commons.notification.domain.Notification;

public record NotificationView(String id, String type, String status, Instant createdAt,
                               String groupId, String groupName, String invitedByName,
                               String archiveRequestId, String itemId, String itemTitle,
                               String message) {

    public static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getType().name(), n.getStatus().name(),
                n.getCreatedAt(), n.getGroupId(), n.getGroupName(), n.getInvitedByName(),
                n.getArchiveRequestId(), n.getItemId(), n.getItemTitle(), n.getMessage());
    }
}
