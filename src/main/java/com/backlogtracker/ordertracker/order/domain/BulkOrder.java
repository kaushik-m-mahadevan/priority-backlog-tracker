package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bulk order (design §8/§9) — one customer, one or more variants, quantity split across
 * one or more creators. Distinct from {@link Order} (an individual order) in two ways: it
 * has variants instead of a single item, and its due date is driven by the
 * slowest-loaded assigned creator rather than one primary creator.
 */
@Document("orderTrackerBulkOrders")
@CompoundIndex(name = "group_orderNumber", def = "{'groupId': 1, 'orderNumber': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkOrder {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private String orderNumber;
    private String customerId;
    private String description;

    @Builder.Default
    private List<BulkVariant> variants = new ArrayList<>();
    @Builder.Default
    private List<CreatorSplit> creatorSplits = new ArrayList<>();

    private Packaging packaging;

    private double materialsCost;
    private double overheadPercentage;
    private double profitMarginPercentage;

    @Builder.Default
    private List<Payment> payments = new ArrayList<>();
    private PaymentStatus paymentStatus;

    private OrderStatus status;
    private Instant computedDueDate;

    @Builder.Default
    private List<ShipmentLeg> shipmentPlan = new ArrayList<>();
    @Builder.Default
    private List<ChangeLogEntry> changeLog = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public int totalQuantity() {
        return variants.stream().mapToInt(BulkVariant::getQuantity).sum();
    }
}
