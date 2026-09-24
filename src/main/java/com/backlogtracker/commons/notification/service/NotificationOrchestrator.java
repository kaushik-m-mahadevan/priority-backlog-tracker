package com.backlogtracker.commons.notification.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * ad-4: proactive notify-on-create for the events that previously left the other party to
 * discover them only by manually checking a list or the Admin page — a new group proposal
 * (cost config, order finalization, profit distribution), a pending signup, or a pending
 * password request. Centralizes the two recurring "who gets told" shapes
 * ({@link #notifyOtherMembers} / {@link #notifyAdmins}) that were about to be copy-pasted
 * into five different call sites.
 */
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationService notificationService;
    private final UserService userService;

    /** Every current member of {@code group} except the one who just acted — the shape
     *  behind "someone proposed X, go take a look" across Order Tracker and Finance
     *  Tracker's unanimous-approval proposals. {@code linkPath} may be {@code null}. */
    public void notifyOtherMembers(Group group, String actingUserId, NotificationType type,
                                   String title, String message, String linkPath) {
        for (String memberId : group.getMemberIds()) {
            if (!memberId.equals(actingUserId)) {
                notificationService.info(memberId, type, title, message, linkPath);
            }
        }
    }

    /** Same recipients as {@link #notifyOtherMembers}, but for a proposal that genuinely
     *  needs each of them to act (counts toward their badge) rather than a plain FYI —
     *  {@code referenceId} lets the caller later clear these via
     *  {@link NotificationService#resolveByReference}/{@code resolveOneByReference} once
     *  votes come in. */
    public void notifyOtherMembersActionable(Group group, String actingUserId, NotificationType type,
                                             String title, String message, String linkPath, String referenceId) {
        for (String memberId : group.getMemberIds()) {
            if (!memberId.equals(actingUserId)) {
                notificationService.actionable(memberId, type, title, message, linkPath, referenceId);
            }
        }
    }

    /** Every admin — new signups and password requests both need one to act, and nothing
     *  told them one was waiting except manually checking Admin. {@code linkPath} may be
     *  {@code null}. */
    public void notifyAdmins(NotificationType type, String title, String message, String linkPath) {
        for (User admin : userService.admins()) {
            notificationService.info(admin.getId(), type, title, message, linkPath);
        }
    }
}
