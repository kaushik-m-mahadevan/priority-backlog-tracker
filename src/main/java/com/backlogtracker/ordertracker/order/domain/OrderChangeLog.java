package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured, per-field change history (spec §12, design principle #9: "change history is
 * structured by field, not a generic log"). One document per order; each named array only
 * gets an entry when that specific field actually changes.
 */
@Document("orderChangeLogs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderChangeLog {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    @Builder.Default
    private List<StatusChange> orderStatusChangeHistory = new ArrayList<>();
    @Builder.Default
    private List<AssigneeChange> assigneeChangeHistory = new ArrayList<>();
    @Builder.Default
    private List<DateChange> deliveryDateChangeHistory = new ArrayList<>();
    @Builder.Default
    private List<PaymentStatusChange> paymentStatusChangeHistory = new ArrayList<>();
    @Builder.Default
    private List<QuantityChange> quantityChangeHistory = new ArrayList<>();
    @Builder.Default
    private List<SplitReallocationChange> splitReallocationHistory = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChange {
        private OrderStatus status;
        private String changedByCreatorId;
        private Instant changeTimestamp;
        /** ad-3: only set for a backward column move (or a cancellation's reason) — a
         *  same-column or forward-column move never requires one. */
        private String justification;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeChange {
        private String stageKey;
        private String oldValue;
        private String newValue;
        private String changedByCreatorId;
        private Instant changeTimestamp;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateChange {
        private Instant oldValue;
        private Instant newValue;
        private String changedByCreatorId;
        private Instant changeTimestamp;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentStatusChange {
        private PaymentStatus oldValue;
        private PaymentStatus newValue;
        private String changedByCreatorId;
        private Instant changeTimestamp;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuantityChange {
        private int oldValue;
        private int newValue;
        private String variantId;
        private String changedByCreatorId;
        private Instant changeTimestamp;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitReallocationChange {
        private String variantId;
        @Builder.Default
        private List<Order.SplitLine> oldAllocation = new ArrayList<>();
        @Builder.Default
        private List<Order.SplitLine> newAllocation = new ArrayList<>();
        private String changedByCreatorId;
        private Instant changeTimestamp;
    }
}
