package com.backlogtracker.notification.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.group.domain.Group;
import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.notification.domain.Notification;
import com.backlogtracker.notification.domain.NotificationStatus;
import com.backlogtracker.notification.domain.NotificationType;
import com.backlogtracker.notification.repository.NotificationRepository;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final GroupService groupService;
    private final UserRepository users;

    public List<Notification> list(String userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long pendingCount(String userId) {
        return notifications.countByUserIdAndStatus(userId, NotificationStatus.PENDING);
    }

    /** Member invites {@code to} (an email or a handle) to {@code groupId}. */
    public Notification createGroupInvite(String groupId, String to, AuthUser inviter) {
        Group g = groupService.requireMember(groupId, inviter.id());

        String needle = to.trim();
        User target = (needle.contains("@")
                ? users.findByEmailIgnoreCase(needle)
                : users.findByHandleIgnoreCase(needle))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active user with that email or handle"));

        if (target.getStatus() == AccountStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No active user with that email or handle");
        }
        if (target.getId().equals(inviter.id()) || g.hasMember(target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    target.getName() + " is already in this group");
        }
        int cap = groupService.maxGroupsPerUser();
        if (cap > 0 && groupService.groupCount(target.getId()) >= cap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    target.getName() + " is already in the maximum number of groups");
        }
        if (notifications.existsByUserIdAndGroupIdAndTypeAndStatus(
                target.getId(), groupId, NotificationType.GROUP_INVITE, NotificationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    target.getName() + " already has a pending invite to this group");
        }

        return notifications.save(Notification.builder()
                .userId(target.getId())
                .type(NotificationType.GROUP_INVITE)
                .status(NotificationStatus.PENDING)
                .groupId(groupId)
                .groupName(g.getName())
                .invitedByUserId(inviter.id())
                .invitedByName(inviter.name())
                .build());
    }

    public Notification accept(String id, String userId) {
        Notification n = require(id, userId);
        groupService.addMember(n.getGroupId(), userId);
        n.setStatus(NotificationStatus.ACCEPTED);
        n.setActedAt(Instant.now());
        return notifications.save(n);
    }

    public Notification decline(String id, String userId) {
        Notification n = require(id, userId);
        n.setStatus(NotificationStatus.DECLINED);
        n.setActedAt(Instant.now());
        return notifications.save(n);
    }

    private Notification require(String id, String userId) {
        Notification n = notifications.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }
        if (n.getStatus() != NotificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already handled");
        }
        return n;
    }
}
