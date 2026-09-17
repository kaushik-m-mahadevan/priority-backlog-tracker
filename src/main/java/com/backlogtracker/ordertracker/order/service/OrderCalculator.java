package com.backlogtracker.ordertracker.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.backlogtracker.ordertracker.master.domain.BusinessConfig.WorkStageType;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Order.BulkDetails;
import com.backlogtracker.ordertracker.order.domain.Order.LineItem;
import com.backlogtracker.ordertracker.order.domain.Order.MandatoryItem;
import com.backlogtracker.ordertracker.order.domain.Order.Packaging;
import com.backlogtracker.ordertracker.order.domain.Order.PaymentEntry;
import com.backlogtracker.ordertracker.order.domain.Order.StageAssignment;
import com.backlogtracker.ordertracker.order.domain.Order.StageProgress;
import com.backlogtracker.ordertracker.order.domain.Order.Variant;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.domain.PaymentType;

/**
 * Pure math for cost/time estimation, due dates, completion percentages, and payment
 * status (spec §5.10, §5.11, §6, §9) — no I/O, fully unit-testable.
 */
@Component
public class OrderCalculator {

    public double mandatoryItemsCost(List<MandatoryItem> items) {
        return items.stream().mapToDouble(i -> i.getUnitCost() * i.getQuantity()).sum();
    }

    public double lineItemsCost(List<LineItem> items) {
        return LineItem.sumCost(items);
    }

    /** grossCost = mandatoryItemsCost + addOnsCost + packagingCost (spec §5.10). */
    public double grossCost(double mandatoryItemsCost, double addOnsCost, double packagingCost) {
        return mandatoryItemsCost + addOnsCost + packagingCost;
    }

    public double overheadAmount(double grossCost, double overheadPercentage) {
        return grossCost * overheadPercentage;
    }

    public double profitAmount(double grossCost, double overheadAmount, double profitMarginPercentage) {
        return (grossCost + overheadAmount) * profitMarginPercentage;
    }

    public double finalCost(double grossCost, double overheadAmount, double profitAmount) {
        return grossCost + overheadAmount + profitAmount;
    }

    /** grossTimeHours = crocheting + assembly + packaging time. Research time is added
     *  separately by the caller — it's a one-time, order-level investment, not something
     *  multiplied per unit the way a bulk variant's crocheting/assembly time is. */
    public double grossTimeHours(double crochetingTimeHours, double assemblyTimeHours, Packaging packaging) {
        return crochetingTimeHours + assemblyTimeHours + packaging.timeHours();
    }

    /** A component's own materials + add-ons cost and crafting time, scaled by its
     *  quantity — no overhead or profit margin applied here, since that's applied once on
     *  the owning order/variant's combined total, not per component (a component isn't
     *  separately billed, it's a piece of one billed item). */
    public Order.Component priceComponent(Order.Component component) {
        double perUnitCost = mandatoryItemsCost(component.getMandatoryItems()) + lineItemsCost(component.getAddOns());
        component.setPerUnitCost(perUnitCost);
        component.setTotalCost(perUnitCost * component.getQuantity());
        component.setPerUnitTimeHours(component.getCraftingTimeHours());
        component.setTotalTimeHours(component.getCraftingTimeHours() * component.getQuantity());
        return component;
    }

    public double componentsCost(List<Order.Component> components) {
        return components.stream().mapToDouble(Order.Component::getTotalCost).sum();
    }

    public double componentsTimeHours(List<Order.Component> components) {
        return components.stream().mapToDouble(Order.Component::getTotalTimeHours).sum();
    }

    /** Builds the full computed snapshot for an individual order (spec §5.10, extended with
     *  assembly, research time, and optional components). {@code mandatoryItems}/
     *  {@code craftingTimeHours} cover a simple, non-decomposed order; {@code components}
     *  (when present) contribute their own materials/add-ons cost and crafting time on top —
     *  additive, not a replacement, so a partially-decomposed order (some shared base
     *  materials plus a few repeatable pieces) still adds up correctly. */
    public Order.CostEstimate estimateIndividual(List<MandatoryItem> mandatoryItems, List<LineItem> addOns,
                                                 List<Order.Component> components,
                                                 Packaging packaging, double craftingTimeHours,
                                                 double assemblyTimeHours, double researchTimeHours,
                                                 double overheadPct, double profitMarginPct,
                                                 Instant orderReceivedDate, double assignedCreatorHoursPerDay) {
        double mandatoryItemsCost = mandatoryItemsCost(mandatoryItems);
        double addOnsCost = lineItemsCost(addOns);
        double componentsCost = componentsCost(components);
        double packagingCost = packaging.cost();
        double gross = grossCost(mandatoryItemsCost + componentsCost, addOnsCost, packagingCost);
        double overhead = overheadAmount(gross, overheadPct);
        double profit = profitAmount(gross, overhead, profitMarginPct);
        double finalCost = finalCost(gross, overhead, profit);
        double componentsTimeHours = componentsTimeHours(components);
        double grossTimeHours = grossTimeHours(craftingTimeHours + componentsTimeHours, assemblyTimeHours, packaging)
                + researchTimeHours;

        return Order.CostEstimate.builder()
                .mandatoryItemsCost(mandatoryItemsCost)
                .addOnsCost(addOnsCost)
                .packagingCost(packagingCost)
                .grossCost(gross)
                .overheadAmount(overhead)
                .profitAmount(profit)
                .finalCost(finalCost)
                .grossTimeHours(grossTimeHours)
                .itemizedBreakdown(List.of(
                        new Order.BreakdownLine("Mandatory items", mandatoryItemsCost),
                        new Order.BreakdownLine("Components", componentsCost),
                        new Order.BreakdownLine("Add-ons", addOnsCost),
                        new Order.BreakdownLine("Packaging", packagingCost),
                        new Order.BreakdownLine("Overhead", overhead),
                        new Order.BreakdownLine("Profit margin", profit)))
                .computedDueDate(dueDate(orderReceivedDate, grossTimeHours, assignedCreatorHoursPerDay))
                .build();
    }

    private Instant dueDate(Instant from, double hours, double hoursPerDay) {
        long days = Math.max(1, (long) Math.ceil(hours / hoursPerDay));
        return from.plus(days, ChronoUnit.DAYS);
    }

    /** Individual-order completion = equal-weighted average of each stage's own
     *  (unitsCompleted / totalUnits) (spec §5.11's stated default). */
    public double individualCompletionFraction(List<StageAssignment> stageAssignments) {
        if (stageAssignments.isEmpty()) {
            return 0.0;
        }
        return stageAssignments.stream()
                .mapToDouble(s -> s.getTotalUnits() <= 0 ? 0.0 : (double) s.getUnitsCompleted() / s.getTotalUnits())
                .average().orElse(0.0);
    }

    // ---- Bulk orders (spec §8/§9) ----

    /** Computes a variant's per-unit and total cost/time and returns an updated copy of the
     *  variant with those fields (and each line's own cost/time) filled in — the caller
     *  saves the result. */
    public Variant priceVariant(Variant variant, double overheadPct, double profitMarginPct) {
        variant.getComponents().forEach(this::priceComponent);
        double mandatoryItemsCost = mandatoryItemsCost(variant.getMandatoryItems());
        double addOnsCost = lineItemsCost(variant.getAddOns());
        double componentsCost = componentsCost(variant.getComponents());
        double packagingCost = variant.getPackaging() == null ? 0 : variant.getPackaging().cost();
        double gross = grossCost(mandatoryItemsCost + componentsCost, addOnsCost, packagingCost);
        double overhead = overheadAmount(gross, overheadPct);
        double profit = profitAmount(gross, overhead, profitMarginPct);
        double perUnitCost = finalCost(gross, overhead, profit);
        double componentsTimeHours = componentsTimeHours(variant.getComponents());
        double perUnitTimeHours = grossTimeHours(variant.getCraftingTimeHours() + componentsTimeHours, variant.getAssemblyTimeHours(),
                variant.getPackaging() == null ? Order.Packaging.builder().itemizedList(List.of()).build() : variant.getPackaging());

        variant.setPerUnitCost(perUnitCost);
        variant.setTotalCost(perUnitCost * variant.getQuantity());
        variant.setPerUnitTimeHours(perUnitTimeHours);
        variant.setTotalTimeHours(perUnitTimeHours * variant.getQuantity());
        return variant;
    }

    /** creatorTotalHours = Σ (their quantity in a variant × that variant's perUnitTimeHours),
     *  summed across every variant they contribute to (spec §9 step 3). */
    public double creatorTotalHours(String creatorId, List<Variant> variants) {
        double total = 0;
        for (Variant v : variants) {
            for (Order.SplitLine split : v.getSplitAllocation()) {
                if (split.getCreatorId().equals(creatorId)) {
                    total += split.getQuantityAssigned() * v.getPerUnitTimeHours();
                }
            }
        }
        return total;
    }

    public record CreatorWorkload(double hours, double hoursPerDay) {
    }

    /** Converts a one-time, order-level hours investment (e.g. research time) into extra
     *  whole days at a given creator's pace — used to pad a bulk order's due date the same
     *  way the logistics buffer already does, since research isn't per-unit the way a
     *  variant's own crocheting/assembly time is. */
    public long extraDaysFor(double hours, double hoursPerDay) {
        return hours <= 0 ? 0 : (long) Math.ceil(hours / hoursPerDay);
    }

    /** computedDueDate = orderReceivedDate + max(creatorDueOffsetDays across all involved
     *  creators) + a manual buffer if the shipment plan includes an INTERNAL_TRANSFER stop
     *  before final delivery (spec §9 steps 4-5). */
    public Instant computeBulkDueDate(Instant orderReceivedDate, List<CreatorWorkload> workloads, int bufferDays) {
        long maxDays = workloads.stream()
                .mapToLong(w -> Math.max(1, (long) Math.ceil(w.hours() / w.hoursPerDay())))
                .max().orElse(1);
        return orderReceivedDate.plus(maxDays + bufferDays, ChronoUnit.DAYS);
    }

    /**
     * Bulk completion (spec §5.11): for a split-tracked stage, sum {@code unitsCompleted}
     * across every creator/variant for that stage and divide by {@code totalQuantity}; for
     * a batch-tracked stage, use its single {@link StageProgress} entry directly. Then
     * average across all configured stages (equal weighting, same default as individual).
     */
    public double bulkCompletionFraction(BulkDetails details, List<WorkStageType> workStages) {
        if (workStages.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (WorkStageType stage : workStages) {
            if (stage.splitTracked()) {
                int completed = 0;
                for (Variant v : details.getVariants()) {
                    for (Order.SplitLine split : v.getSplitAllocation()) {
                        completed += split.getStageProgress().stream()
                                .filter(sp -> sp.getStageKey().equals(stage.stageKey()))
                                .mapToInt(Order.StageProgressEntry::getUnitsCompleted)
                                .sum();
                    }
                }
                sum += details.getTotalQuantity() <= 0 ? 0.0 : (double) completed / details.getTotalQuantity();
            } else {
                sum += details.getStageProgress().stream()
                        .filter(sp -> sp.getStageKey().equals(stage.stageKey()))
                        .findFirst()
                        .map(sp -> sp.getTotalUnits() <= 0 ? 0.0 : (double) sp.getUnitsCompleted() / sp.getTotalUnits())
                        .orElse(0.0);
            }
        }
        return sum / workStages.size();
    }

    // ---- Payments (spec §6) ----

    /** netPaid = Σ(amount where type != REFUND) − Σ(amount where type = REFUND). */
    public double netPaid(List<PaymentEntry> payments) {
        double collected = payments.stream().filter(p -> p.getType() != PaymentType.REFUND)
                .mapToDouble(PaymentEntry::amountValue).sum();
        double refunded = payments.stream().filter(p -> p.getType() == PaymentType.REFUND)
                .mapToDouble(PaymentEntry::amountValue).sum();
        return collected - refunded;
    }

    public double balanceAmount(List<PaymentEntry> payments, double finalCost) {
        return finalCost - netPaid(payments);
    }

    /** UNPAID (netPaid <= 0) -> PARTIALLY_PAID (0 < netPaid < finalCost) -> PAID_IN_FULL
     *  (netPaid >= finalCost) -> REFUNDED (netPaid < 0, or netPaid == 0 with at least one
     *  REFUND entry — the exact-cancel-out case the literal formula alone can't
     *  distinguish from "never paid"; see {@link PaymentStatus#REFUNDED}). */
    public PaymentStatus derivePaymentStatus(List<PaymentEntry> payments, double finalCost) {
        double net = netPaid(payments);
        boolean hasRefund = payments.stream().anyMatch(p -> p.getType() == PaymentType.REFUND);

        if (net <= 0) {
            return hasRefund ? PaymentStatus.REFUNDED : PaymentStatus.UNPAID;
        }
        if (net >= finalCost) {
            return PaymentStatus.PAID_IN_FULL;
        }
        return PaymentStatus.PARTIALLY_PAID;
    }
}
