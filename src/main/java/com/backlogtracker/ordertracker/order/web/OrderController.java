package com.backlogtracker.ordertracker.order.web;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.order.domain.OrderChangeLog;
import com.backlogtracker.ordertracker.order.dto.AddPaymentRequest;
import com.backlogtracker.ordertracker.order.dto.AddTimeLogEntryRequest;
import com.backlogtracker.ordertracker.order.dto.AddUsageLogEntryRequest;
import com.backlogtracker.ordertracker.order.dto.CancelOrderRequest;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.MarkShipmentStopRequest;
import com.backlogtracker.ordertracker.order.dto.OrderFinalizationView;
import com.backlogtracker.ordertracker.order.dto.OrderView;
import com.backlogtracker.ordertracker.order.dto.ProposeOrderFinalizationRequest;
import com.backlogtracker.ordertracker.order.dto.ShipmentPlanRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkDetailsRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkSplitProgressRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateBulkStageProgressRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateOrderStatusRequest;
import com.backlogtracker.ordertracker.order.dto.UpdateStageAssignmentRequest;
import com.backlogtracker.ordertracker.order.service.OrderFinalizationService;
import com.backlogtracker.ordertracker.order.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/orders")
@RequiresUser
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderFinalizationService orderFinalizationService;

    @GetMapping
    public List<OrderView> all(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.all(groupId, actor.id());
    }

    @GetMapping("/my-work")
    public List<OrderView> myWork(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor,
                                  @RequestParam(required = false) String completionFilter,
                                  @RequestParam(required = false) Instant from,
                                  @RequestParam(required = false) Instant to) {
        return orderService.myWork(groupId, actor.id(), completionFilter, from, to);
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable String groupId, @PathVariable String orderId,
                         @AuthenticationPrincipal AuthUser actor) {
        return orderService.get(groupId, actor.id(), orderId);
    }

    @GetMapping("/{orderId}/change-log")
    public List<OrderChangeLog> changeLog(@PathVariable String groupId, @PathVariable String orderId,
                                          @AuthenticationPrincipal AuthUser actor) {
        return orderService.getChangeLog(groupId, actor.id(), orderId);
    }

    @PostMapping
    public OrderView create(@PathVariable String groupId, @RequestBody CreateOrderRequest request,
                            @AuthenticationPrincipal AuthUser actor) {
        return orderService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{orderId}")
    public OrderView update(@PathVariable String groupId, @PathVariable String orderId,
                            @RequestBody UpdateOrderRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.update(groupId, actor.id(), orderId, request);
    }

    @PutMapping("/{orderId}/bulk-details")
    public OrderView updateBulkDetails(@PathVariable String groupId, @PathVariable String orderId,
                                       @RequestBody UpdateBulkDetailsRequest request,
                                       @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateBulkDetails(groupId, actor.id(), orderId, request);
    }

    @PatchMapping("/{orderId}/status")
    public OrderView updateStatus(@PathVariable String groupId, @PathVariable String orderId,
                                  @RequestBody UpdateOrderStatusRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateStatus(groupId, actor.id(), orderId, request);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderView cancel(@PathVariable String groupId, @PathVariable String orderId,
                            @RequestBody CancelOrderRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.cancelOrder(groupId, actor.id(), orderId, request);
    }

    @PatchMapping("/{orderId}/stage-assignments/{stageKey}")
    public OrderView updateStageAssignment(@PathVariable String groupId, @PathVariable String orderId,
                                           @PathVariable String stageKey, @RequestBody UpdateStageAssignmentRequest request,
                                           @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateStageAssignment(groupId, actor.id(), orderId, stageKey, request);
    }

    @PatchMapping("/{orderId}/bulk-stage-progress/{stageKey}")
    public OrderView updateBulkStageProgress(@PathVariable String groupId, @PathVariable String orderId,
                                             @PathVariable String stageKey,
                                             @RequestBody UpdateBulkStageProgressRequest request,
                                             @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateBulkStageProgress(groupId, actor.id(), orderId, stageKey, request);
    }

    @PatchMapping("/{orderId}/bulk-split-progress")
    public OrderView updateBulkSplitProgress(@PathVariable String groupId, @PathVariable String orderId,
                                             @RequestBody UpdateBulkSplitProgressRequest request,
                                             @AuthenticationPrincipal AuthUser actor) {
        return orderService.updateBulkSplitProgress(groupId, actor.id(), orderId, request);
    }

    @PostMapping("/{orderId}/payments")
    public OrderView addPayment(@PathVariable String groupId, @PathVariable String orderId,
                                @RequestBody AddPaymentRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.addPayment(groupId, actor.id(), orderId, request);
    }

    @DeleteMapping("/{orderId}/payments/{paymentId}")
    public OrderView removePayment(@PathVariable String groupId, @PathVariable String orderId,
                                   @PathVariable String paymentId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.removePayment(groupId, actor.id(), orderId, paymentId);
    }

    @PostMapping("/{orderId}/time-log")
    public OrderView addTimeLogEntry(@PathVariable String groupId, @PathVariable String orderId,
                                     @RequestBody AddTimeLogEntryRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.addTimeLogEntry(groupId, actor.id(), orderId, request);
    }

    @DeleteMapping("/{orderId}/time-log/{entryId}")
    public OrderView removeTimeLogEntry(@PathVariable String groupId, @PathVariable String orderId,
                                        @PathVariable String entryId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.removeTimeLogEntry(groupId, actor.id(), orderId, entryId);
    }

    @PostMapping("/{orderId}/usage-log")
    public OrderView addUsageLogEntry(@PathVariable String groupId, @PathVariable String orderId,
                                      @RequestBody AddUsageLogEntryRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.addUsageLogEntry(groupId, actor.id(), orderId, request);
    }

    @DeleteMapping("/{orderId}/usage-log/{entryId}")
    public OrderView removeUsageLogEntry(@PathVariable String groupId, @PathVariable String orderId,
                                         @PathVariable String entryId, @AuthenticationPrincipal AuthUser actor) {
        return orderService.removeUsageLogEntry(groupId, actor.id(), orderId, entryId);
    }

    @PutMapping("/{orderId}/shipment-plan")
    public OrderView setShipmentPlan(@PathVariable String groupId, @PathVariable String orderId,
                                     @RequestBody ShipmentPlanRequest request, @AuthenticationPrincipal AuthUser actor) {
        return orderService.setShipmentPlan(groupId, actor.id(), orderId, request);
    }

    @PatchMapping("/{orderId}/shipment-plan/{stopIndex}")
    public OrderView markShipmentStop(@PathVariable String groupId, @PathVariable String orderId,
                                      @PathVariable int stopIndex, @RequestBody MarkShipmentStopRequest request,
                                      @AuthenticationPrincipal AuthUser actor) {
        return orderService.markShipmentStop(groupId, actor.id(), orderId, stopIndex, request);
    }

    @GetMapping("/{orderId}/finalization")
    public OrderFinalizationView getFinalization(@PathVariable String groupId, @PathVariable String orderId,
                                                 @AuthenticationPrincipal AuthUser actor) {
        return orderFinalizationService.get(groupId, actor.id(), orderId);
    }

    @PostMapping("/{orderId}/finalization/propose")
    public OrderFinalizationView proposeFinalization(@PathVariable String groupId, @PathVariable String orderId,
                                                      @RequestBody ProposeOrderFinalizationRequest request,
                                                      @AuthenticationPrincipal AuthUser actor) {
        return orderFinalizationService.propose(groupId, actor.id(), orderId, request);
    }

    @PostMapping("/{orderId}/finalization/approve")
    public OrderFinalizationView approveFinalization(@PathVariable String groupId, @PathVariable String orderId,
                                                      @AuthenticationPrincipal AuthUser actor) {
        return orderFinalizationService.approve(groupId, actor.id(), orderId);
    }

    @PostMapping("/{orderId}/finalization/reject")
    public OrderFinalizationView rejectFinalization(@PathVariable String groupId, @PathVariable String orderId,
                                                     @AuthenticationPrincipal AuthUser actor) {
        return orderFinalizationService.reject(groupId, actor.id(), orderId);
    }
}
