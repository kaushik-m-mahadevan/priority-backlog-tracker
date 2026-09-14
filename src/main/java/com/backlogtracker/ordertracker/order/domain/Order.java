package com.backlogtracker.ordertracker.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.backlogtracker.commons.crypto.EncryptedString;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One base schema shared by individual and bulk orders (spec §5, design principle #4) —
 * {@code orderType} discriminates, and {@link BulkDetails} is populated only when
 * {@code orderType == BULK}. For a bulk order, {@code mandatoryItems}/{@code addOns}/
 * {@code packaging}/{@code craftingTimeHours}/{@code costEstimate}/{@code stageAssignments}
 * live per-variant inside {@code bulkDetails} instead (spec §8) and are left empty here;
 * {@code pattern}/{@code researchItems}/{@code recipeSteps}/{@code payments}/
 * {@code shipmentPlan} apply at the order level regardless of type.
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

    /** 14-digit [Location:3][Creator:3][OrderType:2][Sequence:6] (spec §5.1). */
    private String orderNumber;
    private OrderType orderType;

    private String customerId;
    /** Who logged the order into the system — encodes the order number's creator segment.
     *  Distinct from who is actually doing the work ({@link #stageAssignments}), which can
     *  differ and can change over the order's life without the order number changing
     *  (spec §5.1, design principle #5). */
    private String createdByCreatorId;

    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // ---- 5.2 order details ----
    private String itemName;
    private Instant orderReceivedDate;
    /** Promised to the customer at intake — distinct from {@code costEstimate.computedDueDate}. */
    private Instant quotedDeliveryDate;
    private Instant actualDeliveryDate;

    // ---- 5.3 pattern ----
    private Pattern pattern;

    // ---- 5.4 research ----
    @Builder.Default
    private List<ResearchItem> researchItems = new ArrayList<>();
    /** One-time, order-level time spent researching the design/pattern — not per unit, so
     *  it applies once regardless of orderType (design-decision extension, not in the
     *  original spec's time formula). */
    private double researchTimeHours;

    // ---- 5.5 mandatory items (individual only; bulk uses per-variant) ----
    @Builder.Default
    private List<MandatoryItem> mandatoryItems = new ArrayList<>();
    /** Tools used (individual only; bulk uses per-variant) — kept out of mandatoryItems
     *  entirely since tools carry no cost/quantity. */
    @Builder.Default
    private List<ToolUsage> tools = new ArrayList<>();

    // ---- 5.6 add-ons (individual only; bulk uses per-variant) ----
    @Builder.Default
    private List<LineItem> addOns = new ArrayList<>();

    // ---- 5.7 packaging (individual only; bulk uses per-variant) ----
    private Packaging packaging;

    // ---- 5.8 prototyping / recipe ----
    @Builder.Default
    private List<String> recipeSteps = new ArrayList<>();
    /** Free-text how-to for assembly and packaging (which materials/tools go where, the
     *  steps to put it together and box it up) — distinct from recipeSteps, which is the
     *  crochet pattern itself, not what happens after the pieces are made. Design-decision
     *  extension, not in the original spec. */
    private String assemblyPackagingInstructions;
    /** Free-text catch-all: customer interactions, things that changed mid-order, or any
     *  other detail that doesn't fit the structured fields above. Design-decision
     *  extension, not in the original spec. */
    private String notes;

    // ---- 5.9 crocheting/assembly time (individual only; bulk uses per-variant) ----
    private double craftingTimeHours;
    private double assemblyTimeHours;

    // ---- 5.10 cost & time estimation snapshot (individual only) ----
    private CostEstimate costEstimate;

    // ---- 5.11 stage assignment (individual only; bulk uses bulkDetails.stageAssignments) ----
    @Builder.Default
    private List<StageAssignment> stageAssignments = new ArrayList<>();

    // ---- 6. payments ledger ----
    @Builder.Default
    private List<PaymentEntry> payments = new ArrayList<>();
    private PaymentStatus paymentStatus;

    // ---- 7. shipment plan ----
    private ShipmentPlan shipmentPlan;

    // ---- 8. bulk-only details ----
    private BulkDetails bulkDetails;

    // =====================================================================================
    // Nested embedded types
    // =====================================================================================

    public enum PatternType { TEMPLATE, CUSTOM }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pattern {
        private PatternType patternType;
        private String templateName;
        private String customPatternNotes;
        @Builder.Default
        private List<String> attachmentUrls = new ArrayList<>();
    }

    public enum ResearchItemType { VIDEO, LINK, IMAGE, NOTE }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResearchItem {
        private ResearchItemType type;
        private String url;
        private String description;
    }

    /** Entries per {@code BusinessConfig.mandatoryItemTypes} (spec §5.5) — plain text/number
     *  entry, no catalog; an order may carry several entries for the same itemKey (e.g. two
     *  wool colours, or two needle sizes). {@code inventoryItemId} is reserved/unused per
     *  spec §11. {@code notes} is an optional free-text explanation of why this particular
     *  entry is needed — most useful for a tool-type item ("size 4 hook for the edging"). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MandatoryItem {
        private String itemKey;
        private String value;
        private double quantity;
        private double unitCost;
        private String notes;
    }

    /** A tool used on this order (e.g. "4mm hook") — separate from {@link MandatoryItem}
     *  because tools are reused across orders, not purchased or costed per order (matches
     *  {@code MandatoryItemType.isTool} in BusinessConfig). No quantity/cost fields at all,
     *  just which tool and an optional note. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolUsage {
        private String itemKey;
        private String value;
        private String notes;
    }

    /** Shared sub-schema used for add-ons and packaging's finalized itemized list (spec §3).
     *  {@code inventoryItemId} is reserved/unused per spec §11. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String name;
        private String category;
        @Builder.Default
        private Map<String, String> attributes = Map.of();
        private double quantity;
        private double unitCost;
        private Double unitTimeHours;
        private String note;
    }

    /** {@code tentativePresetId} references a group's {@code PresetOption}; its cost/time are
     *  snapshotted here at save time so a later preset edit never retroactively changes a
     *  past order. Cost/time rule (spec §5.7): itemized sum if {@code itemizedList} is
     *  non-empty, else the preset's snapshotted estimate. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Packaging {
        private String tentativePresetId;
        private double presetCost;
        private double presetTimeHours;
        @Builder.Default
        private List<LineItem> itemizedList = new ArrayList<>();

        public double cost() {
            return itemizedList.isEmpty() ? presetCost
                    : itemizedList.stream().mapToDouble(li -> li.getUnitCost() * li.getQuantity()).sum();
        }

        public double timeHours() {
            return itemizedList.isEmpty() ? presetTimeHours
                    : itemizedList.stream()
                            .mapToDouble(li -> (li.getUnitTimeHours() == null ? 0 : li.getUnitTimeHours()) * li.getQuantity())
                            .sum();
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownLine {
        private String label;
        private double amount;
    }

    /** Computed snapshot (spec §5.10) — recomputed and overwritten whenever any cost/time
     *  input changes, never hand-edited directly. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostEstimate {
        private double mandatoryItemsCost;
        private double addOnsCost;
        private double packagingCost;
        private double grossCost;
        private double overheadAmount;
        private double profitAmount;
        private double finalCost;
        private double grossTimeHours;
        @Builder.Default
        private List<BreakdownLine> itemizedBreakdown = new ArrayList<>();
        private Instant computedDueDate;
    }

    /** One entry per configured work stage (spec §5.11). {@code unitsCompleted} is 0 or 1
     *  for an individual order ({@code totalUnits} is always 1) — bulk orders track
     *  progress differently, per creator/variant or in a batch, via {@link BulkDetails}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageAssignment {
        private String stageKey;
        private String assignedCreatorId;
        private int unitsCompleted;
        @Builder.Default
        private int totalUnits = 1;
        private Instant lastUpdatedAt;
    }

    /** {@code amount}, {@code mode}, and {@code note} are encrypted per spec §4.5/§6. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentEntry {
        private String paymentId;
        private PaymentType type;
        private EncryptedString amount;
        private Instant date;
        private EncryptedString mode;
        private EncryptedString note;

        public double amountValue() {
            return amount == null || amount.value() == null ? 0.0 : Double.parseDouble(amount.value());
        }

        public String modeValue() {
            return mode == null ? null : mode.value();
        }

        public String noteValue() {
            return note == null ? null : note.value();
        }
    }

    public enum ShipmentStopType { INTERNAL_TRANSFER, FINAL_DELIVERY }

    /** {@code trackingNumber} is encrypted per spec §4.5/§7; {@code carrier} is not. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentStop {
        private int stopOrder;
        private ShipmentStopType type;
        private String originLocationCode;
        private String destinationLocationCode;
        private String laneId;
        private double estimatedCost;
        private double estimatedTimeHours;
        private String carrier;
        private EncryptedString trackingNumber;
        private Instant triggerDate;
        private Instant shippedDate;
        private boolean deliveredConfirmed;

        public String trackingNumberValue() {
            return trackingNumber == null ? null : trackingNumber.value();
        }
    }

    /** One order — individual or bulk — has a single {@code stops} list (spec §7); a bulk
     *  order with a cross-location split typically has two or more, an individual order
     *  typically exactly one, {@code FINAL_DELIVERY}. Shared at the order level, not
     *  per-variant, since the consolidated batch ships together. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentPlan {
        @Builder.Default
        private List<ShipmentStop> stops = new ArrayList<>();
    }

    /** Per-creator, per-variant progress on a batch-split stage (Crocheting/Assembly), e.g.
     *  spec §5.11. {@code totalUnits} is implicitly the owning {@link SplitLine}'s
     *  {@code quantityAssigned}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageProgressEntry {
        private String stageKey;
        private int unitsCompleted;
    }

    /** One creator's share of a variant's quantity (spec §8). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitLine {
        private String creatorId;
        private int quantityAssigned;
        @Builder.Default
        private List<StageProgressEntry> stageProgress = new ArrayList<>();
    }

    /** Progress on a stage that's typically done once across the whole batch (Packaging,
     *  Shipment), tracked at the {@link BulkDetails} level instead of per creator/variant
     *  (spec §5.11). {@code totalUnits} equals {@code bulkDetails.totalQuantity}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageProgress {
        private String stageKey;
        private int unitsCompleted;
        private int totalUnits;
    }

    /** One independently-configured variant within a bulk order (spec §8) — different
     *  colorways/specs genuinely have different materials and possibly different costs. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variant {
        private String variantId;
        private String label;
        private int quantity;
        @Builder.Default
        private List<MandatoryItem> mandatoryItems = new ArrayList<>();
        @Builder.Default
        private List<ToolUsage> tools = new ArrayList<>();
        @Builder.Default
        private List<LineItem> addOns = new ArrayList<>();
        private Packaging packaging;
        private double craftingTimeHours;
        private double assemblyTimeHours;
        private double perUnitCost;
        private double totalCost;
        private double perUnitTimeHours;
        private double totalTimeHours;
        @Builder.Default
        private List<SplitLine> splitAllocation = new ArrayList<>();
    }

    /** Present only when {@code orderType == BULK} (spec §8). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDetails {
        @Builder.Default
        private List<Variant> variants = new ArrayList<>();
        private int totalQuantity;
        private double totalFinalCost;
        private double totalTimeHours;
        /** Who manages consolidation/logistics for this order — distinct from
         *  {@code createdByCreatorId}, which only affects the order number. */
        private String coordinatingCreatorId;
        @Builder.Default
        private List<StageAssignment> stageAssignments = new ArrayList<>();
        @Builder.Default
        private List<StageProgress> stageProgress = new ArrayList<>();
        /** Manual buffer (days) added to the computed due date when the shipment plan
         *  includes an INTERNAL_TRANSFER stop before final delivery (spec §9 step 5). Zero
         *  when there's no consolidation leg. */
        private int logisticsBufferDays;
        /** Spec §9 computes this (max-of-offsets across every involved creator, plus the
         *  logistics buffer) but the §8 field table omits it — kept here since it's the
         *  bulk order's promised-delivery date, same role as the individual order's
         *  {@code costEstimate.computedDueDate}. */
        private Instant computedDueDate;
    }
}
