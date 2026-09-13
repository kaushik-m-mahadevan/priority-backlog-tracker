package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.domain.BulkOrder;
import com.backlogtracker.ordertracker.order.domain.BulkVariant;
import com.backlogtracker.ordertracker.order.domain.ChangeLogEntry;
import com.backlogtracker.ordertracker.order.domain.CreatorSplit;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.service.OrderCalculator;

public record BulkOrderView(String id, String orderNumber, String customerId, String description,
                            List<BulkVariant> variants, int totalQuantity, List<CreatorSplit> creatorSplits,
                            double overallCompletionPercent, double materialsCost, double packagingCost,
                            double totalCost, List<PaymentView> payments, PaymentStatus paymentStatus,
                            OrderStatus status, Instant computedDueDate, List<ShipmentLegView> shipmentPlan,
                            List<ChangeLogEntry> changeLog, Instant createdAt, Instant updatedAt) {

    public static BulkOrderView of(BulkOrder o, OrderCalculator calc) {
        double totalCost = calc.totalCost(o.getMaterialsCost(), o.getPackaging(),
                o.getOverheadPercentage(), o.getProfitMarginPercentage());
        int totalQty = o.getCreatorSplits().stream().mapToInt(CreatorSplit::getAssignedQuantity).sum();
        double overall = totalQty <= 0 ? 0.0 : o.getCreatorSplits().stream()
                .mapToDouble(s -> s.getCompletionFraction() * ((double) s.getAssignedQuantity() / totalQty))
                .sum();
        return new BulkOrderView(o.getId(), o.getOrderNumber(), o.getCustomerId(), o.getDescription(),
                o.getVariants(), o.totalQuantity(), o.getCreatorSplits(), overall * 100,
                o.getMaterialsCost(), o.getPackaging() == null ? 0 : o.getPackaging().cost(), totalCost,
                o.getPayments().stream().map(PaymentView::of).toList(), o.getPaymentStatus(), o.getStatus(),
                o.getComputedDueDate(), o.getShipmentPlan().stream().map(ShipmentLegView::of).toList(),
                o.getChangeLog(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
