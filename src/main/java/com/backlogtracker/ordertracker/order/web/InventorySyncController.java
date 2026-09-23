package com.backlogtracker.ordertracker.order.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.order.service.OrderService;

import lombok.RequiredArgsConstructor;

/** ad-2: the caller's own "sync to inventory" sweep — group-scoped rather than per-order,
 *  since it applies pending usage across every one of their orders in this business at once. */
@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/inventory-sync")
@RequiresUser
@RequiredArgsConstructor
public class InventorySyncController {

    private final OrderService orderService;

    @GetMapping("/preview")
    public Map<String, Double> preview(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.previewPendingSync(groupId, actor.id());
    }

    @PostMapping("/apply")
    public Map<String, Double> apply(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.applyPendingSync(groupId, actor.id());
    }
}
