package com.backlogtracker.commons.notification.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.notification.domain.Notification;
import com.backlogtracker.commons.notification.dto.NotificationView;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiresUser
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final GroupRepository groups;

    /** The caller's inbox, newest first, with a pending count. One batch lookup for every
     *  distinct groupId in the page (mb-2) instead of one query per notification. */
    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser actor) {
        List<Notification> notifications = notificationService.list(actor.id());
        Map<String, String> appletKeyByGroupId = appletKeysFor(notifications);
        List<NotificationView> items = notifications.stream()
                .map(n -> NotificationView.of(n, appletKeyByGroupId.get(n.getGroupId())))
                .toList();
        return Map.of("items", items, "pending", notificationService.pendingCount(actor.id()));
    }

    @PostMapping("/{id}/accept")
    public NotificationView accept(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        Notification n = notificationService.accept(id, actor.id());
        return NotificationView.of(n, appletKeyFor(n.getGroupId()));
    }

    @PostMapping("/{id}/decline")
    public NotificationView decline(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        Notification n = notificationService.decline(id, actor.id());
        return NotificationView.of(n, appletKeyFor(n.getGroupId()));
    }

    private Map<String, String> appletKeysFor(List<Notification> notifications) {
        List<String> groupIds = notifications.stream().map(Notification::getGroupId).filter(java.util.Objects::nonNull).distinct().toList();
        return groups.findAllById(groupIds).stream().collect(Collectors.toMap(Group::getId, Group::getAppletKey));
    }

    private String appletKeyFor(String groupId) {
        return groupId == null ? null : groups.findById(groupId).map(Group::getAppletKey).orElse(null);
    }
}
