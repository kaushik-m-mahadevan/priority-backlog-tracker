package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.commons.pattern.domain.PatternType;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;

public record OrderView(String id, String orderNumber, OrderType orderType, String customerId,
                        String createdByCreatorId, OrderStatus status,
                        String itemName, Instant orderReceivedDate, Instant quotedDeliveryDate,
                        Instant actualDeliveryDate, PatternView pattern, List<ResearchItemView> researchItems,
                        double researchTimeHours, String assemblyPackagingInstructions,
                        String notes, List<MandatoryItemView> mandatoryItems, List<LineItemView> addOns,
                        PackagingView packaging, double craftingTimeHours, double assemblyTimeHours,
                        CostEstimateView costEstimate,
                        List<StageAssignmentView> stageAssignments, double completionPercentage,
                        List<PaymentView> payments, PaymentStatus paymentStatus, double netPaid,
                        double balanceAmount, List<ShipmentStopView> shipmentPlan,
                        BulkDetailsView bulkDetails, Instant createdAt, Instant updatedAt) {

    /** {@code recipeSteps} lives here now, not as a separate top-level order field — see
     *  {@link Pattern} for why they're merged. */
    public record PatternView(PatternType patternType, String templateName, String customPatternNotes,
                              List<String> attachmentUrls, List<String> recipeSteps) {
        static PatternView of(Pattern p) {
            return p == null ? null : new PatternView(p.getPatternType(), p.getTemplateName(),
                    p.getCustomPatternNotes(), p.getAttachmentUrls(), p.getRecipeSteps());
        }
    }

    public record ResearchItemView(Order.ResearchItemType type, String url, String description) {
        static ResearchItemView of(Order.ResearchItem r) {
            return new ResearchItemView(r.getType(), r.getUrl(), r.getDescription());
        }
    }

    public record MandatoryItemView(Order.MaterialKind kind, String value, double quantity, double unitCost,
                                    String notes, String linkedYarnTypeId, String linkedNeedleTypeId) {
        static MandatoryItemView of(Order.MandatoryItem m) {
            return new MandatoryItemView(m.getKind(), m.getValue(), m.getQuantity(), m.getUnitCost(), m.getNotes(),
                    m.getLinkedYarnTypeId(), m.getLinkedNeedleTypeId());
        }
    }

    public record LineItemView(String name, String category, java.util.Map<String, String> attributes,
                               double quantity, double unitCost, Double unitTimeHours, String note) {
        static LineItemView of(Order.LineItem li) {
            return new LineItemView(li.getName(), li.getCategory(), li.getAttributes(), li.getQuantity(),
                    li.getUnitCost(), li.getUnitTimeHours(), li.getNote());
        }
    }

    public record PackagingView(String tentativePresetId, double cost, double timeHours,
                                List<LineItemView> itemizedList) {
        static PackagingView of(Order.Packaging p) {
            if (p == null) {
                return null;
            }
            return new PackagingView(p.getTentativePresetId(), p.cost(), p.timeHours(),
                    p.getItemizedList().stream().map(LineItemView::of).toList());
        }
    }

    public record BreakdownLineView(String label, double amount) {
        static BreakdownLineView of(Order.BreakdownLine b) {
            return new BreakdownLineView(b.getLabel(), b.getAmount());
        }
    }

    public record CostEstimateView(double mandatoryItemsCost, double addOnsCost, double packagingCost,
                                   double grossCost, double overheadAmount, double profitAmount,
                                   double finalCost, double grossTimeHours, List<BreakdownLineView> itemizedBreakdown,
                                   Instant computedDueDate) {
        static CostEstimateView of(Order.CostEstimate c) {
            if (c == null) {
                return null;
            }
            return new CostEstimateView(c.getMandatoryItemsCost(), c.getAddOnsCost(), c.getPackagingCost(),
                    c.getGrossCost(), c.getOverheadAmount(), c.getProfitAmount(), c.getFinalCost(),
                    c.getGrossTimeHours(), c.getItemizedBreakdown().stream().map(BreakdownLineView::of).toList(),
                    c.getComputedDueDate());
        }
    }

    public record StageAssignmentView(String stageKey, String assignedCreatorId, int unitsCompleted,
                                      int totalUnits, Instant lastUpdatedAt) {
        static StageAssignmentView of(Order.StageAssignment s) {
            return new StageAssignmentView(s.getStageKey(), s.getAssignedCreatorId(), s.getUnitsCompleted(),
                    s.getTotalUnits(), s.getLastUpdatedAt());
        }
    }

    public record PaymentView(String paymentId, com.backlogtracker.ordertracker.order.domain.PaymentType type,
                              double amount, Instant date, String mode, String note) {
        static PaymentView of(Order.PaymentEntry p) {
            return new PaymentView(p.getPaymentId(), p.getType(), p.amountValue(), p.getDate(), p.modeValue(),
                    p.noteValue());
        }
    }

    public record ShipmentStopView(int stopOrder, Order.ShipmentStopType type, String originLocationCode,
                                   String destinationLocationCode, String laneId, double estimatedCost,
                                   double estimatedTimeHours, String carrier, String trackingNumber,
                                   Instant triggerDate, Instant shippedDate, boolean deliveredConfirmed) {
        static ShipmentStopView of(Order.ShipmentStop s) {
            return new ShipmentStopView(s.getStopOrder(), s.getType(), s.getOriginLocationCode(),
                    s.getDestinationLocationCode(), s.getLaneId(), s.getEstimatedCost(), s.getEstimatedTimeHours(),
                    s.getCarrier(), s.trackingNumberValue(), s.getTriggerDate(), s.getShippedDate(),
                    s.isDeliveredConfirmed());
        }
    }

    public record StageProgressEntryView(String stageKey, int unitsCompleted) {
        static StageProgressEntryView of(Order.StageProgressEntry e) {
            return new StageProgressEntryView(e.getStageKey(), e.getUnitsCompleted());
        }
    }

    public record SplitLineView(String creatorId, int quantityAssigned, List<StageProgressEntryView> stageProgress) {
        static SplitLineView of(Order.SplitLine s) {
            return new SplitLineView(s.getCreatorId(), s.getQuantityAssigned(),
                    s.getStageProgress().stream().map(StageProgressEntryView::of).toList());
        }
    }

    public record VariantView(String variantId, String label, int quantity, List<MandatoryItemView> mandatoryItems,
                              List<LineItemView> addOns, PackagingView packaging, double craftingTimeHours,
                              double assemblyTimeHours,
                              double perUnitCost, double totalCost, double perUnitTimeHours, double totalTimeHours,
                              List<SplitLineView> splitAllocation) {
        static VariantView of(Order.Variant v) {
            return new VariantView(v.getVariantId(), v.getLabel(), v.getQuantity(),
                    v.getMandatoryItems().stream().map(MandatoryItemView::of).toList(),
                    v.getAddOns().stream().map(LineItemView::of).toList(),
                    PackagingView.of(v.getPackaging()), v.getCraftingTimeHours(), v.getAssemblyTimeHours(),
                    v.getPerUnitCost(), v.getTotalCost(), v.getPerUnitTimeHours(), v.getTotalTimeHours(),
                    v.getSplitAllocation().stream().map(SplitLineView::of).toList());
        }
    }

    public record BulkStageProgressView(String stageKey, int unitsCompleted, int totalUnits) {
        static BulkStageProgressView of(Order.StageProgress p) {
            return new BulkStageProgressView(p.getStageKey(), p.getUnitsCompleted(), p.getTotalUnits());
        }
    }

    public record BulkDetailsView(List<VariantView> variants, int totalQuantity, double totalFinalCost,
                                  double totalTimeHours, String coordinatingCreatorId,
                                  List<StageAssignmentView> stageAssignments,
                                  List<BulkStageProgressView> stageProgress, int logisticsBufferDays,
                                  Instant computedDueDate) {
        static BulkDetailsView of(Order.BulkDetails d) {
            if (d == null) {
                return null;
            }
            return new BulkDetailsView(d.getVariants().stream().map(VariantView::of).toList(), d.getTotalQuantity(),
                    d.getTotalFinalCost(), d.getTotalTimeHours(), d.getCoordinatingCreatorId(),
                    d.getStageAssignments().stream().map(StageAssignmentView::of).toList(),
                    d.getStageProgress().stream().map(BulkStageProgressView::of).toList(), d.getLogisticsBufferDays(),
                    d.getComputedDueDate());
        }
    }

    public static OrderView of(Order o, double completionPercentage, double netPaid, double balanceAmount) {
        return new OrderView(o.getId(), o.getOrderNumber(), o.getOrderType(), o.getCustomerId(),
                o.getCreatedByCreatorId(), o.getStatus(), o.getItemName(), o.getOrderReceivedDate(),
                o.getQuotedDeliveryDate(), o.getActualDeliveryDate(), PatternView.of(o.getPattern()),
                o.getResearchItems().stream().map(ResearchItemView::of).toList(), o.getResearchTimeHours(),
                o.getAssemblyPackagingInstructions(), o.getNotes(),
                o.getMandatoryItems().stream().map(MandatoryItemView::of).toList(),
                o.getAddOns().stream().map(LineItemView::of).toList(), PackagingView.of(o.getPackaging()),
                o.getCraftingTimeHours(), o.getAssemblyTimeHours(), CostEstimateView.of(o.getCostEstimate()),
                o.getStageAssignments().stream().map(StageAssignmentView::of).toList(), completionPercentage,
                o.getPayments().stream().map(PaymentView::of).toList(), o.getPaymentStatus(), netPaid, balanceAmount,
                o.getShipmentPlan() == null ? List.of()
                        : o.getShipmentPlan().getStops().stream().map(ShipmentStopView::of).toList(),
                BulkDetailsView.of(o.getBulkDetails()), o.getCreatedAt(), o.getUpdatedAt());
    }
}
