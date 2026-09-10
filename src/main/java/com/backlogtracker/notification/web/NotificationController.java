package com.backlogtracker.notification.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.notification.dto.NotificationView;
import com.backlogtracker.notification.service.NotificationService;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiresUser
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** The caller's inbox, newest first, with a pending count. */
    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser actor) {
        List<NotificationView> items = notificationService.list(actor.id()).stream()
                .map(NotificationView::of)
                .toList();
        return Map.of("items", items, "pending", notificationService.pendingCount(actor.id()));
    }

    @PostMapping("/{id}/accept")
    public NotificationView accept(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return NotificationView.of(notificationService.accept(id, actor.id()));
    }

    @PostMapping("/{id}/decline")
    public NotificationView decline(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return NotificationView.of(notificationService.decline(id, actor.id()));
    }
}
