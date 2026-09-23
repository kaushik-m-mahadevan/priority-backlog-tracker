package com.backlogtracker.ordertracker.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.web.ScopedLookup;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.dto.OrderFinalizationView;
import com.backlogtracker.ordertracker.order.dto.ProposeOrderFinalizationRequest;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the generic {@link ApprovalService} to Order Tracker's own order-actuals
 * finalization (design decision: "we need all to accept their final values for an
 * order"). One order has at most one PENDING finalization request at a time, scoped by
 * {@code kind = "ordertracker:order-finalization:<orderId>"} so unrelated orders' (or the
 * group's cost-config) approvals never collide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFinalizationService {

    private static final String KIND_PREFIX = "ordertracker:order-finalization:";

    private final OrderRepository orderRepository;
    private final GroupService groupService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final Clock clock;

    public OrderFinalizationView get(String groupId, String userId, String orderId) {
        Group group = groupService.requireMember(groupId, userId);
        Order order = requireOrder(groupId, orderId);
        return view(order, group, userId);
    }

    public OrderFinalizationView propose(String groupId, String userId, String orderId,
                                         ProposeOrderFinalizationRequest request) {
        Group group = groupService.requireMember(groupId, userId);
        Order order = requireOrder(groupId, orderId);
        if (order.getFinalization() != null && order.getFinalization().getStatus() == Order.FinalizationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A finalization proposal is already pending approval for this order");
        }
        double finalProfit = request.finalRevenue() - request.finalCost();
        ApprovalRequest approval = approvalService.propose(groupId, userId, kind(orderId), Map.of(
                "finalCost", request.finalCost(),
                "finalRevenue", request.finalRevenue(),
                "finalProfit", finalProfit));
        order.setFinalization(Order.Finalization.builder()
                .status(Order.FinalizationStatus.PENDING)
                .approvalRequestId(approval.getId())
                .finalCost(request.finalCost())
                .finalRevenue(request.finalRevenue())
                .finalProfit(finalProfit)
                .build());
        applyIfResolved(order, approval, group, userId);
        orderRepository.save(order);
        if (approval.getStatus() == ApprovalStatus.PENDING) {
            notificationOrchestrator.notifyOtherMembers(group, userId, NotificationType.ORDER_FINALIZATION_PROPOSED,
                    "A final cost/revenue proposal for order " + order.getOrderNumber() + " is waiting for your approval.");
        }
        return view(order, group, userId);
    }

    public OrderFinalizationView approve(String groupId, String userId, String orderId) {
        Group group = groupService.requireMember(groupId, userId);
        Order order = requireOrder(groupId, orderId);
        String requestId = requirePendingRequestId(order);
        ApprovalRequest approval = approvalService.approve(groupId, userId, requestId);
        applyIfResolved(order, approval, group, userId);
        orderRepository.save(order);
        return view(order, group, userId);
    }

    public OrderFinalizationView reject(String groupId, String userId, String orderId) {
        Group group = groupService.requireMember(groupId, userId);
        Order order = requireOrder(groupId, orderId);
        String requestId = requirePendingRequestId(order);
        approvalService.reject(groupId, userId, requestId);
        order.setFinalization(Order.Finalization.builder().status(Order.FinalizationStatus.NONE).build());
        orderRepository.save(order);
        return view(order, group, userId);
    }

    private void applyIfResolved(Order order, ApprovalRequest approval, Group group, String userId) {
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            return;
        }
        double finalCost = ((Number) approval.getPayload().get("finalCost")).doubleValue();
        double finalRevenue = ((Number) approval.getPayload().get("finalRevenue")).doubleValue();
        double finalProfit = ((Number) approval.getPayload().get("finalProfit")).doubleValue();
        order.setFinalization(Order.Finalization.builder()
                .status(Order.FinalizationStatus.FINALIZED)
                .approvalRequestId(approval.getId())
                .finalCost(finalCost)
                .finalRevenue(finalRevenue)
                .finalProfit(finalProfit)
                .finalizedAt(Instant.now(clock))
                .build());
        // mb-12: nothing previously told anyone a finalization actually resolved — only
        // the propose (ORDER_FINALIZATION_PROPOSED) and invalidate cases were covered.
        // Excludes only whoever's own action just resolved it (matches
        // notifyOtherMembers' existing shape); the original proposer, if someone else,
        // still gets told the moment it locks in rather than having to go check manually.
        notificationOrchestrator.notifyOtherMembers(group, userId, NotificationType.ORDER_FINALIZATION_RESOLVED,
                "Order " + order.getOrderNumber() + "'s final cost/revenue is now locked in.");
    }

    /** A member leaving mid-approval invalidates the underlying ApprovalRequest generically
     *  — this brings the order's own denormalized {@code finalization.status} back in sync
     *  and notifies the proposer, mirroring CostConfigChangeService's own listener. */
    @EventListener
    public void onApprovalInvalidated(ApprovalRequestInvalidatedEvent event) {
        if (!event.kind().startsWith(KIND_PREFIX)) {
            return;
        }
        String orderId = event.kind().substring(KIND_PREFIX.length());
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getFinalization() == null || order.getFinalization().getStatus() != Order.FinalizationStatus.PENDING) {
                return;
            }
            order.setFinalization(Order.Finalization.builder().status(Order.FinalizationStatus.NONE).build());
            orderRepository.save(order);
            notificationService.info(event.proposedByUserId(), NotificationType.ORDER_FINALIZATION_INVALIDATED,
                    "Your proposed final cost/revenue for order " + order.getOrderNumber()
                            + " was cancelled because a member left the group mid-approval. You can propose it again.");
            log.info("Order finalization for order {} invalidated — approval request {}", orderId, event.requestId());
        });
    }

    private String requirePendingRequestId(Order order) {
        if (order.getFinalization() == null || order.getFinalization().getStatus() != Order.FinalizationStatus.PENDING
                || order.getFinalization().getApprovalRequestId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No finalization proposal is pending for this order");
        }
        return order.getFinalization().getApprovalRequestId();
    }

    private OrderFinalizationView view(Order order, Group group, String userId) {
        final Order.Finalization f = order.getFinalization() == null
                ? Order.Finalization.builder().status(Order.FinalizationStatus.NONE).build()
                : order.getFinalization();
        String proposedByUserId = null;
        List<String> approvedByUserIds = List.of();
        if (f.getStatus() == Order.FinalizationStatus.PENDING && f.getApprovalRequestId() != null) {
            ApprovalRequest approval = approvalService.list(group.getId(), userId, kind(order.getId())).stream()
                    .filter(r -> r.getId().equals(f.getApprovalRequestId())).findFirst().orElse(null);
            if (approval != null) {
                proposedByUserId = approval.getProposedByUserId();
                approvedByUserIds = approval.getApprovedByUserIds();
            }
        }
        return new OrderFinalizationView(f.getStatus(), f.getFinalCost(), f.getFinalRevenue(), f.getFinalProfit(),
                f.getFinalizedAt(), proposedByUserId, approvedByUserIds, group.getMemberIds());
    }

    private String kind(String orderId) {
        return KIND_PREFIX + orderId;
    }

    private Order requireOrder(String groupId, String orderId) {
        return ScopedLookup.requireInGroup(orderRepository.findById(orderId), Order::getGroupId, groupId, "Order not found");
    }
}
