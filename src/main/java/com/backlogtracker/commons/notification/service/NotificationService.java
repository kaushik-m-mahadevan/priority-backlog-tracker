package com.backlogtracker.commons.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.Notification;
import com.backlogtracker.commons.notification.domain.NotificationStatus;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.dto.PendingInviteView;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

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

    /** Types the recipient can act on — these drive the bell's badge count. */
    private static final Set<NotificationType> ACTIONABLE =
            EnumSet.of(NotificationType.GROUP_INVITE, NotificationType.ARCHIVE_REQUEST);

    public long pendingCount(String userId) {
        return notifications.countByUserIdAndStatusAndTypeIn(
                userId, NotificationStatus.PENDING, ACTIONABLE);
    }

    /** One inbox entry per other group member, asking them to approve/reject the archive.
     *  Takes plain fields rather than an {@code ArchiveRequest} — {@code commons} never
     *  depends on an applet's domain classes, only the other way around. */
    public void noticeArchiveRequest(String archiveRequestId, String groupId, String itemId, String itemTitle,
                                     String requestedByName, String note, Collection<String> recipientUserIds) {
        for (String uid : recipientUserIds) {
            notifications.save(Notification.builder()
                    .userId(uid)
                    .type(NotificationType.ARCHIVE_REQUEST)
                    .status(NotificationStatus.PENDING)
                    .archiveRequestId(archiveRequestId)
                    .groupId(groupId)
                    .itemId(itemId)
                    .itemTitle(itemTitle)
                    .invitedByName(requestedByName)
                    .message(note)
                    .build());
        }
    }

    /** Clear one member's open ARCHIVE_REQUEST notice once their vote is recorded. */
    public void markArchiveVoteCast(String archiveRequestId, String userId, boolean approved) {
        for (Notification n : notifications.findByArchiveRequestId(archiveRequestId)) {
            if (n.getUserId().equals(userId)
                    && n.getType() == NotificationType.ARCHIVE_REQUEST
                    && n.getStatus() == NotificationStatus.PENDING) {
                n.setStatus(approved ? NotificationStatus.ACCEPTED : NotificationStatus.DECLINED);
                n.setActedAt(Instant.now());
                notifications.save(n);
            }
        }
    }

    /** Close every open notice for a resolved request and post an informational result.
     *  Takes plain fields rather than an {@code ArchiveRequest} — see
     *  {@link #noticeArchiveRequest} for why. */
    public void resolveArchiveRequest(String archiveRequestId, String groupId, String itemId, String itemTitle,
                                      boolean approved, String rejectedByName,
                                      Collection<String> memberUserIds) {
        Instant now = Instant.now();
        for (Notification n : notifications.findByArchiveRequestId(archiveRequestId)) {
            if (n.getType() == NotificationType.ARCHIVE_REQUEST
                    && n.getStatus() == NotificationStatus.PENDING) {
                n.setStatus(approved ? NotificationStatus.ACCEPTED : NotificationStatus.DECLINED);
                n.setActedAt(now);
                notifications.save(n);
            }
        }
        String text = approved
                ? "“" + itemTitle + "” was archived."
                : "The request to archive “" + itemTitle + "” was declined"
                        + (rejectedByName != null ? " by " + rejectedByName : "")
                        + ".";
        for (String uid : memberUserIds) {
            notifications.save(Notification.builder()
                    .userId(uid)
                    .type(NotificationType.ARCHIVE_RESULT)
                    .status(NotificationStatus.ACCEPTED) // informational — never counts as pending
                    .archiveRequestId(archiveRequestId)
                    .groupId(groupId)
                    .itemId(itemId)
                    .itemTitle(itemTitle)
                    .message(text)
                    .build());
        }
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
        int cap = groupService.maxGroupsPerApplet(g.getAppletKey());
        if (cap > 0 && groupService.groupCount(target.getId(), g.getAppletKey()) >= cap) {
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

    /** View-only list of a group's outstanding invites — no cancel/revoke action yet
     *  (design decision, revisit once real email delivery exists). */
    public List<PendingInviteView> pendingInvites(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return notifications.findByGroupIdAndTypeAndStatusOrderByCreatedAtDesc(
                        groupId, NotificationType.GROUP_INVITE, NotificationStatus.PENDING)
                .stream()
                .map(n -> {
                    User invitee = users.findById(n.getUserId()).orElse(null);
                    String display = invitee == null ? "(unknown)"
                            : invitee.getHandle() != null && !invitee.getHandle().isBlank()
                                    ? "@" + invitee.getHandle() : invitee.getEmail();
                    return new PendingInviteView(n.getId(), display, n.getInvitedByName(), n.getCreatedAt());
                })
                .toList();
    }

    /** A one-line informational notice (no action). Never counts toward the badge. */
    public void info(String userId, NotificationType type, String message) {
        notifications.save(Notification.builder()
                .userId(userId)
                .type(type)
                .status(NotificationStatus.ACCEPTED)
                .message(message)
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
