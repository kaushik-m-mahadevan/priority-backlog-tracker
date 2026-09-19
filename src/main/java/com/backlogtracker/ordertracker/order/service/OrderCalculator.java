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

    /** grossCost = mandatoryItemsCost + addOnsCost + packagingCost + laborCost (round 5
     *  pricing redesign — labor is treated as a raw-material-like cost, not overhead or
     *  profit, so it's folded in here before {@link #profitAmount} applies). */
    public double grossCost(double mandatoryItemsCost, double addOnsCost, double packagingCost, double laborCost) {
        return mandatoryItemsCost + addOnsCost + packagingCost + laborCost;
    }

    /** totalHours × the business's effective hourly wage (round 5 pricing redesign) — the
     *  first time hours have ever fed into cost; previously time only affected the due
     *  date. */
    public double laborCost(double totalHours, double hourlyWage) {
        return totalHours * hourlyWage;
    }

    /** Overhead is a delivery-time concept only now (round 5 redesign) — no overhead
     *  argument here any more, profit margin applies directly on top of gross. */
    public double profitAmount(double grossCost, double profitMarginPercentage) {
        return grossCost * profitMarginPercentage;
    }

    public double finalCost(double grossCost, double profitAmount) {
        return grossCost + profitAmount;
    }

    /** ceil(hours / hoursPerDay), minimum 1 day of work for any non-zero order. */
    public int workDays(double hours, double hoursPerDay) {
        return (int) Math.max(1, Math.ceil(hours / hoursPerDay));
    }

    /** The quotable delivery date (round 5 redesign): the work-days estimate plus a flat,
     *  delivery-tier buffer, then padded by the business's time-overhead percentage and
     *  rounded up — e.g. 2 work days + 1 delivery-buffer day = 3, × 1.15 overhead = 3.45,
     *  rounds up to 4. */
    public Instant quotableDeliveryDate(Instant orderReceivedDate, int workDays, int deliveryBufferDays,
                                        double overheadPercentage) {
        double padded = (workDays + deliveryBufferDays) * (1 + overheadPercentage);
        long totalDays = Math.max(1, (long) Math.ceil(padded));
        return orderReceivedDate.plus(totalDays, ChronoUnit.DAYS);
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
                                                 double overheadPct, double profitMarginPct, double hourlyWage,
                                                 int deliveryBufferDays,
                                                 Instant orderReceivedDate, double assignedCreatorHoursPerDay) {
        double mandatoryItemsCost = mandatoryItemsCost(mandatoryItems);
        double addOnsCost = lineItemsCost(addOns);
        double componentsCost = componentsCost(components);
        double packagingCost = packaging.cost();
        double componentsTimeHours = componentsTimeHours(components);
        double grossTimeHours = grossTimeHours(craftingTimeHours + componentsTimeHours, assemblyTimeHours, packaging)
                + researchTimeHours;
        double labor = laborCost(grossTimeHours, hourlyWage);
        double gross = grossCost(mandatoryItemsCost + componentsCost, addOnsCost, packagingCost, labor);
        double profit = profitAmount(gross, profitMarginPct);
        double finalCost = finalCost(gross, profit);
        int workDays = workDays(grossTimeHours, assignedCreatorHoursPerDay);

        return Order.CostEstimate.builder()
                .mandatoryItemsCost(mandatoryItemsCost)
                .addOnsCost(addOnsCost)
                .packagingCost(packagingCost)
                .laborCost(labor)
                .grossCost(gross)
                .profitAmount(profit)
                .finalCost(finalCost)
                .grossTimeHours(grossTimeHours)
                .workDays(workDays)
                .deliveryBufferDays(deliveryBufferDays)
                .itemizedBreakdown(List.of(
                        new Order.BreakdownLine("Mandatory items", mandatoryItemsCost),
                        new Order.BreakdownLine("Components", componentsCost),
                        new Order.BreakdownLine("Add-ons", addOnsCost),
                        new Order.BreakdownLine("Packaging", packagingCost),
                        new Order.BreakdownLine("Labor", labor),
                        new Order.BreakdownLine("Profit margin", profit)))
                .computedDueDate(quotableDeliveryDate(orderReceivedDate, workDays, deliveryBufferDays, overheadPct))
                .build();
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
    public Variant priceVariant(Variant variant, double profitMarginPct, double hourlyWage) {
        variant.getComponents().forEach(this::priceComponent);
        double mandatoryItemsCost = mandatoryItemsCost(variant.getMandatoryItems());
        double addOnsCost = lineItemsCost(variant.getAddOns());
        double componentsCost = componentsCost(variant.getComponents());
        double packagingCost = variant.getPackaging() == null ? 0 : variant.getPackaging().cost();
        double componentsTimeHours = componentsTimeHours(variant.getComponents());
        double perUnitTimeHours = grossTimeHours(variant.getCraftingTimeHours() + componentsTimeHours, variant.getAssemblyTimeHours(),
                variant.getPackaging() == null ? Order.Packaging.builder().itemizedList(List.of()).build() : variant.getPackaging());
        double labor = laborCost(perUnitTimeHours, hourlyWage);
        double gross = grossCost(mandatoryItemsCost + componentsCost, addOnsCost, packagingCost, labor);
        double profit = profitAmount(gross, profitMarginPct);
        double perUnitCost = finalCost(gross, profit);

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

    /** Round 5 redesign: computedDueDate = max(creatorDueOffsetDays across all involved
     *  creators) + the manual logistics buffer (internal-transfer leg, spec §9 steps 4-5) +
     *  the delivery-tier buffer (round 5), all padded by the time-overhead percentage and
     *  rounded up — same shape as the individual-order formula. */
    public Instant computeBulkDueDate(Instant orderReceivedDate, List<CreatorWorkload> workloads,
                                      int logisticsBufferDays, int deliveryBufferDays, double overheadPercentage) {
        long maxDays = workloads.stream()
                .mapToLong(w -> Math.max(1, (long) Math.ceil(w.hours() / w.hoursPerDay())))
                .max().orElse(1);
        double padded = (maxDays + logisticsBufferDays + deliveryBufferDays) * (1 + overheadPercentage);
        long totalDays = Math.max(1, (long) Math.ceil(padded));
        return orderReceivedDate.plus(totalDays, ChronoUnit.DAYS);
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
