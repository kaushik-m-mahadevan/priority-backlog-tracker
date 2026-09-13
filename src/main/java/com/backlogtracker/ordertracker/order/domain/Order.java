package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * An individual crochet order (design §5) — one customer, one item, one primary creator.
 * Bulk orders (design §8/§9: variants, per-creator split) are a separate aggregate, not
 * modeled here.
 */
@Document("orderTrackerOrders")
@CompoundIndex(name = "group_orderNumber", def = "{'groupId': 1, 'orderNumber': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String id;

    @Indexed
    private String groupId;

    /** 14-digit [Location:3][Creator:3][OrderType:2][Sequence:6] (design spec, confirmed
     *  bump from the mockup's stale 12-digit/4-digit-sequence examples). */
    private String orderNumber;

    private String customerId;
    /** Governs {@code computedDueDate} for this order (platform integration decision:
     *  one primary creator's capacity, not a max across every assigned creator). */
    private String primaryCreatorId;

    private String description;
    /** itemKey -> value, one entry expected per BusinessConfig.mandatoryItemTypes at
     *  creation time. */
    private Map<String, String> mandatoryItems;
    private List<String> addOns;

    private Packaging packaging;

    @Builder.Default
    private List<StageProgress> stageProgress = new ArrayList<>();

    private double materialsCost;
    private double overheadPercentage;
    private double profitMarginPercentage;

    @Builder.Default
    private List<Payment> payments = new ArrayList<>();
    private PaymentStatus paymentStatus;

    private OrderStatus status;
    private Instant computedDueDate;

    @Builder.Default
    private List<ChangeLogEntry> changeLog = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;
}
