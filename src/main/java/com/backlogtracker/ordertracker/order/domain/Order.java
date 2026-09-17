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
import com.backlogtracker.commons.pattern.domain.Pattern;

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
 * {@code pattern} (which now carries its own recipe steps — see
 * {@link com.backlogtracker.commons.pattern.domain.Pattern})/{@code researchItems}/
 * {@code payments}/{@code shipmentPlan} apply at the order level regardless of type.
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

    // ---- 5.5 mandatory items (individual only; bulk uses per-variant). Only meaningful
    // when {@link #components} is empty — an order broken into components keeps its
    // materials on each component instead (design decision, opt-in: a simple order with
    // nothing to decompose keeps using this flat list; a decomposed one keeps this empty
    // and uses components exclusively). ----
    @Builder.Default
    private List<MandatoryItem> mandatoryItems = new ArrayList<>();

    // ---- 5.6 add-ons (individual only; bulk uses per-variant). Once {@link #components}
    // is in use, these are specifically the assembly step's own extras ("Finishing
    // touches" in the UI — glue, backing card) — distinct from any one component's own
    // add-ons, which live on that {@link Component} instead. ----
    @Builder.Default
    private List<LineItem> addOns = new ArrayList<>();

    // ---- components: an order (or, for bulk, a variant — see Variant#components) can
    // optionally be broken into repeatable atomic pieces built separately and assembled
    // later (design decision, opt-in — see class doc on Component). Empty for a simple
    // order that doesn't need decomposing. ----
    @Builder.Default
    private List<Component> components = new ArrayList<>();

    // ---- 5.7 packaging (individual only; bulk uses per-variant) ----
    private Packaging packaging;

    // ---- 5.8 prototyping / recipe (recipeSteps lives on the shared Pattern now — see
    // commons.pattern.domain.Pattern) ----
    /** Free-text how-to for assembly and packaging (which materials/tools go where, the
     *  steps to put it together and box it up) — distinct from Pattern.recipeSteps, which
     *  is the crochet pattern itself, not what happens after the pieces are made. Design-decision
     *  extension, not in the original spec. */
    private String assemblyPackagingInstructions;
    /** Free-text catch-all: customer interactions, things that changed mid-order, or any
     *  other detail that doesn't fit the structured fields above. Design-decision
     *  extension, not in the original spec. */
    private String notes;

    // ---- 5.9 crocheting/assembly time (individual only; bulk uses per-variant) ----
    private double craftingTimeHours;
    private double assemblyTimeHours;

    /** Actual hours logged against {@link #researchTimeHours}, plus — for an
     *  {@code INDIVIDUAL} order only — {@link #craftingTimeHours}/{@link #assemblyTimeHours}
     *  too (bulk orders log crafting/assembly per variant instead, matching how those
     *  estimates are already split — see {@link Variant#timeLogEntries}). Compared against
     *  the matching estimate field to show over/under, never fed back into cost math. */
    @Builder.Default
    private List<TimeLogEntry> timeLogEntries = new ArrayList<>();

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

    // ---- order-finalization consensus (design decision: every member must accept the
    // order's final actual cost/revenue before it's treated as settled, same unanimous
    // mechanics as the cost-config change request) ----
    private Finalization finalization;

    // =====================================================================================
    // Nested embedded types
    // =====================================================================================

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

    /** Exactly two kinds of mandatory item exist (design decision: descope the previously
     *  configurable, freeform mandatory-item-type list down to these two) — each links to
     *  its own matching Material Inventory catalog rather than sharing one generic link
     *  field. */
    public enum MaterialKind { YARN, NEEDLE }

    /** Plain text/number entry, no catalog of its own; an order may carry several entries
     *  of the same {@code kind} (e.g. two wool colours, or two needle sizes). {@code notes}
     *  is an optional free-text explanation of why this particular entry is needed. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MandatoryItem {
        private MaterialKind kind;
        private String value;
        private double quantity;
        private double unitCost;
        private String notes;
        /** Only meaningful when {@code kind == YARN} and this business has a linked
         *  Material Inventory group. Points at a {@code YarnType} id in that (separate-
         *  applet) group; Order Tracker's backend never validates or interprets it, just
         *  stores and returns it opaquely (no cross-applet import), consistent with the
         *  whole platform's Group/GroupLink model. The frontend does the actual lookup and
         *  the resulting on-hand-vs-needed shortfall comparison. */
        private String linkedYarnTypeId;
        /** Same idea as {@link #linkedYarnTypeId}, but for {@code kind == NEEDLE} — points
         *  at a {@code NeedleType} id instead. */
        private String linkedNeedleTypeId;
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

        /** The one place {@code unitCost * quantity} gets summed across a list of line
         *  items — {@link Packaging#cost()} and {@code OrderCalculator.lineItemsCost}
         *  both delegate here instead of each reimplementing it. */
        public static double sumCost(List<LineItem> items) {
            return items.stream().mapToDouble(i -> i.unitCost * i.quantity).sum();
        }

        /** Same as {@link #sumCost}, for {@code unitTimeHours * quantity}. */
        public static double sumTimeHours(List<LineItem> items) {
            return items.stream().mapToDouble(i -> (i.unitTimeHours == null ? 0 : i.unitTimeHours) * i.quantity).sum();
        }
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
            return itemizedList.isEmpty() ? presetCost : LineItem.sumCost(itemizedList);
        }

        public double timeHours() {
            return itemizedList.isEmpty() ? presetTimeHours : LineItem.sumTimeHours(itemizedList);
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

    /** The three time categories an order already estimates against — {@link #researchTimeHours},
     *  {@link #craftingTimeHours}, {@link #assemblyTimeHours} — kept as an enum here rather
     *  than reusing {@code BusinessConfig.WorkStageType.stageKey} since those are two
     *  different concepts (a business's configurable work-stage pipeline vs. the fixed
     *  three-way time-estimate split every order has always had). */
    public enum TimeStage { RESEARCH, CRAFTING, ASSEMBLY }

    /** One logged real-world work session against a {@link TimeStage} — purely informational
     *  (compared against the matching estimate field to show over/under), never fed back
     *  into cost calculations. {@code date} is when the work happened, which the logger may
     *  backdate (e.g. logging a whole week's crocheting on Friday) — distinct from when the
     *  entry was actually recorded. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeLogEntry {
        private String entryId;
        private TimeStage stage;
        private double hours;
        private Instant date;
        private String loggedByCreatorId;
        private String note;
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
        /** Actual crafting/assembly hours logged against this variant's own estimates —
         *  research isn't here since it's a whole-order concept, see {@link Order#timeLogEntries}. */
        @Builder.Default
        private List<TimeLogEntry> timeLogEntries = new ArrayList<>();
        /** Same opt-in decomposition as {@link Order#components} — a variant is "one
         *  whole repeatable item," and that item can optionally be broken into its own
         *  atomic pieces too (design decision: two variants can share the same component
         *  structure — same patterns — while differing only in, say, yarn color per
         *  component). Empty for a variant that doesn't need decomposing. */
        @Builder.Default
        private List<Component> components = new ArrayList<>();
    }

    /** One repeatable atomic piece of an order or bulk variant — a vase base, a lily, a
     *  sunflower — built separately (its own pattern, materials, add-ons, crochet time)
     *  and assembled with the others later (design decision, opt-in — see
     *  {@link Order#components}/{@link Variant#components}). {@code templateId} references
     *  a {@code ComponentTemplate} (Order Tracker master data) for the reusable pattern and
     *  a typical crafting time; {@code label} and {@code templateCraftingTimeHours} are
     *  snapshotted from it at pick-time so a later template edit never retroactively
     *  changes a past order — same convention as {@link Packaging#tentativePresetId}.
     *  Everything else here is specific to this order: which yarn/color was actually used,
     *  this instance's own add-ons (e.g. floral wire for a stem), and the actual crafting
     *  time for this order (pre-filled from the snapshot, editable). Packaging is
     *  deliberately not here — it stays a single step at the order/variant level, not
     *  per-component. {@code perUnitCost}/{@code totalCost}/{@code perUnitTimeHours}/
     *  {@code totalTimeHours} are computed the same way {@link Variant}'s are: no overhead
     *  or profit margin applied at this level — that's applied once, on the order/variant's
     *  combined total, not per component. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Component {
        private String componentId;
        private String templateId;
        private String label;
        private double templateCraftingTimeHours;
        private int quantity;
        @Builder.Default
        private List<MandatoryItem> mandatoryItems = new ArrayList<>();
        @Builder.Default
        private List<LineItem> addOns = new ArrayList<>();
        private double craftingTimeHours;
        private double perUnitCost;
        private double totalCost;
        private double perUnitTimeHours;
        private double totalTimeHours;
        @Builder.Default
        private List<TimeLogEntry> timeLogEntries = new ArrayList<>();
    }

    public enum FinalizationStatus { NONE, PENDING, FINALIZED }

    /** Locks in the order's actual cost/revenue once every current group member has
     *  unanimously approved them, via {@code commons.approval}. {@code approvalRequestId}
     *  is only meaningful while {@code status == PENDING}; once {@code FINALIZED}, the
     *  {@code final*} fields are the settled record and don't change unless someone
     *  proposes and unanimously re-approves a new finalization. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finalization {
        @Builder.Default
        private FinalizationStatus status = FinalizationStatus.NONE;
        private String approvalRequestId;
        private double finalCost;
        private double finalRevenue;
        private double finalProfit;
        private Instant finalizedAt;
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
