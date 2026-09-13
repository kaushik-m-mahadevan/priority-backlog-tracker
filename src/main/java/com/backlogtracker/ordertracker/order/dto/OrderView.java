package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.domain.StageProgress;
import com.backlogtracker.ordertracker.order.service.OrderCalculator;

public record OrderView(String id, String orderNumber, String customerId, String primaryCreatorId,
                        String description, Map<String, String> mandatoryItems, List<String> addOns,
                        List<StageProgress> stageProgress, double overallCompletionPercent,
                        double materialsCost, double packagingCost, double totalCost,
                        List<PaymentView> payments, PaymentStatus paymentStatus,
                        OrderStatus status, Instant computedDueDate, Instant createdAt, Instant updatedAt) {

    public static OrderView of(Order o, OrderCalculator calc) {
        double totalCost = calc.totalCost(o);
        return new OrderView(o.getId(), o.getOrderNumber(), o.getCustomerId(), o.getPrimaryCreatorId(),
                o.getDescription(), o.getMandatoryItems(), o.getAddOns(),
                o.getStageProgress(), calc.overallCompletionFraction(o.getStageProgress()) * 100,
                o.getMaterialsCost(), o.getPackaging() == null ? 0 : o.getPackaging().cost(), totalCost,
                o.getPayments().stream().map(PaymentView::of).toList(), o.getPaymentStatus(),
                o.getStatus(), o.getComputedDueDate(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
