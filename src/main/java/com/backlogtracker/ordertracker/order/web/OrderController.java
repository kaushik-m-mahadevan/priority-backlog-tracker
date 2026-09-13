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
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderStatusRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateStageRequest;
import com.backlogtracker.ordertracker.order.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/orders")
@RequiresUser
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderView> all(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.all(groupId, actor.id());
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable String groupId, @PathVariable String orderId,
                         @AuthenticationPrincipal AuthUser actor) {
        return orderService.get(groupId, actor.id(), orderId);
    }

    @PostMapping
    public OrderView create(@PathVariable String groupId, @RequestBody CreateOrderRequest request,
                            @AuthenticationPrincipal AuthUser actor) {
        return orderService.create(groupId, actor.id(), request);
    }

    @PatchMapping("/{orderId}/stages/{stageKey}")
    public OrderView updateStage(@PathVariable String groupId, @PathVariable String orderId,
                                 @PathVariable String stageKey, @RequestBody UpdateStageRequest request,
                                 @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateStageProgress(groupId, actor.id(), orderId, stageKey, request);
    }

    @PostMapping("/{orderId}/payments")
    public OrderView addPayment(@PathVariable String groupId, @PathVariable String orderId,
                                @RequestBody AddPaymentRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.addPayment(groupId, actor.id(), orderId, request);
    }

    @PatchMapping("/{orderId}/status")
    public OrderView updateStatus(@PathVariable String groupId, @PathVariable String orderId,
                                  @RequestBody UpdateOrderStatusRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateStatus(groupId, actor.id(), orderId, request);
    }
}
