package com.backlogtracker.ordertracker.order.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.order.service.OrderService;

import lombok.RequiredArgsConstructor;

/** ad-1: re-syncs every historical payment to the linked Finance Tracker group's ledger —
 *  the manual trigger for linking a business that already has payments recorded. */
@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/finance-sync")
@RequiresUser
@RequiredArgsConstructor
public class FinanceSyncController {

    private final OrderService orderService;

    @PostMapping("/backfill")
    public Map<String, Integer> backfill(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.backfillPaymentsToFinance(groupId, actor.id());
    }
}
