package com.backlogtracker.commons.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

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

    /** The bell's badge count — every applet's actionable notices are judged by the same
     *  flag (see {@link com.backlogtracker.commons.notification.domain.Notification#isActionable()}),
     *  not a hardcoded per-type list that a new actionable type could ship without ever
     *  being added to. */
    public long pendingCount(String userId) {
        return notifications.countByUserIdAndStatusAndActionableTrue(userId, NotificationStatus.PENDING);
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
                    .actionable(true)
                    .title("Archive request")
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
                    .title("Archive request")
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
                .actionable(true)
                .title("Group invite")
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

    /** The uniform template every applet's notifications go through: a header, a body, and
     *  (for the two kinds below) whether it needs the recipient to act and where clicking it
     *  should take them. This is the informational half — no action needed, doesn't count
     *  toward the badge. {@code linkPath} may be {@code null} when there's nowhere more
     *  specific to send them than the inbox itself. */
    public void info(String userId, NotificationType type, String title, String message, String linkPath) {
        notifications.save(Notification.builder()
                .userId(userId)
                .type(type)
                .status(NotificationStatus.ACCEPTED)
                .actionable(false)
                .title(title)
                .message(message)
                .linkPath(linkPath)
                .build());
    }

    /** The actionable half of the same template — for a type that needs a response but has
     *  no bespoke accept/decline flow of its own on the {@code Notification} (unlike
     *  GROUP_INVITE/ARCHIVE_REQUEST): it counts toward the badge and links out to wherever
     *  the actual response happens (e.g. the Assignments page), and stays PENDING here until
     *  {@link #resolveByReference} clears it once the real thing is resolved there.
     *  {@code referenceId} is whatever id lets that later lookup find this notice again. */
    public Notification actionable(String userId, NotificationType type, String title, String message,
                                   String linkPath, String referenceId) {
        return notifications.save(Notification.builder()
                .userId(userId)
                .type(type)
                .status(NotificationStatus.PENDING)
                .actionable(true)
                .title(title)
                .message(message)
                .linkPath(linkPath)
                .referenceId(referenceId)
                .build());
    }

    /** Clears every still-pending notice of {@code type} carrying {@code referenceId} —
     *  the generalized version of {@link #markArchiveVoteCast}/{@link #resolveArchiveRequest}
     *  for a type that resolves through its own applet's endpoint rather than through this
     *  notification directly (e.g. accepting a {@code MaterialAssignment} on the Assignments
     *  page). {@code approved} only affects the stored status — the human-readable outcome is
     *  whatever the caller separately posts via {@link #info}. */
    public void resolveByReference(NotificationType type, String referenceId, boolean approved) {
        Instant now = Instant.now();
        for (Notification n : notifications.findByTypeAndReferenceIdAndStatus(type, referenceId, NotificationStatus.PENDING)) {
            n.setStatus(approved ? NotificationStatus.ACCEPTED : NotificationStatus.DECLINED);
            n.setActedAt(now);
            notifications.save(n);
        }
    }

    /** Same as {@link #resolveByReference}, but only for one recipient — for a unanimous
     *  multi-party approval, one member acting doesn't mean the others no longer need to;
     *  only their own notice is done. */
    public void resolveOneByReference(NotificationType type, String referenceId, String userId, boolean approved) {
        Instant now = Instant.now();
        for (Notification n : notifications.findByTypeAndReferenceIdAndStatus(type, referenceId, NotificationStatus.PENDING)) {
            if (n.getUserId().equals(userId)) {
                n.setStatus(approved ? NotificationStatus.ACCEPTED : NotificationStatus.DECLINED);
                n.setActedAt(now);
                notifications.save(n);
            }
        }
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
