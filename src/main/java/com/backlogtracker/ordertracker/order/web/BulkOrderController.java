package com.backlogtracker.ordertracker.order.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.order.dto.AddPaymentRequest;
import com.backlogtracker.ordertracker.order.dto.BulkOrderView;
import com.backlogtracker.ordertracker.order.dto.CreateBulkOrderRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateCreatorSplitRequest;
import com.backlogtracker.ordertracker.order.service.BulkOrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/bulk-orders")
@RequiresUser
@RequiredArgsConstructor
public class BulkOrderController {

    private final BulkOrderService bulkOrderService;

    @GetMapping
    public List<BulkOrderView> all(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return bulkOrderService.all(groupId, actor.id());
    }

    @GetMapping("/{orderId}")
    public BulkOrderView get(@PathVariable String groupId, @PathVariable String orderId,
                             @AuthenticationPrincipal AuthUser actor) {
        return bulkOrderService.get(groupId, actor.id(), orderId);
    }

    @PostMapping
    public BulkOrderView create(@PathVariable String groupId, @RequestBody CreateBulkOrderRequest request,
                                @AuthenticationPrincipal AuthUser actor) {
        return bulkOrderService.create(groupId, actor.id(), request);
    }

    @PatchMapping("/{orderId}/creator-splits/{creatorId}")
    public BulkOrderView updateCreatorSplit(@PathVariable String groupId, @PathVariable String orderId,
                                            @PathVariable String creatorId,
                                            @RequestBody UpdateCreatorSplitRequest request,
                                            @AuthenticationPrincipal AuthUser actor) {
        return bulkOrderService.updateCreatorSplitProgress(groupId, actor.id(), orderId, creatorId, request);
    }

    @PostMapping("/{orderId}/payments")
    public BulkOrderView addPayment(@PathVariable String groupId, @PathVariable String orderId,
                                    @RequestBody AddPaymentRequest request, @AuthenticationPrincipal AuthUser actor) {
        return bulkOrderService.addPayment(groupId, actor.id(), orderId, request);
    }
}
