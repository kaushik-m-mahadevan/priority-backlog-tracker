package com.backlogtracker.ordertracker.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.backlogtracker.commons.crypto.EncryptedString;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.WorkStageType;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.Order.BulkDetails;
import com.backlogtracker.ordertracker.order.domain.Order.LineItem;
import com.backlogtracker.ordertracker.order.domain.Order.MandatoryItem;
import com.backlogtracker.ordertracker.order.domain.Order.Packaging;
import com.backlogtracker.ordertracker.order.domain.Order.PaymentEntry;
import com.backlogtracker.ordertracker.order.domain.Order.SplitLine;
import com.backlogtracker.ordertracker.order.domain.Order.StageAssignment;
import com.backlogtracker.ordertracker.order.domain.Order.StageProgress;
import com.backlogtracker.ordertracker.order.domain.Order.StageProgressEntry;
import com.backlogtracker.ordertracker.order.domain.Order.Variant;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.domain.PaymentType;
import com.backlogtracker.ordertracker.order.service.OrderCalculator.CreatorWorkload;

class OrderCalculatorTest {

    private final OrderCalculator calc = new OrderCalculator();

    private MandatoryItem item(double qty, double unitCost) {
        return MandatoryItem.builder().kind(Order.MaterialKind.YARN).value("cream").quantity(qty).unitCost(unitCost).build();
    }

    private LineItem lineItem(double qty, double unitCost, Double unitTimeHours) {
        return LineItem.builder().name("Safety eyes").quantity(qty).unitCost(unitCost).unitTimeHours(unitTimeHours).build();
    }

    @Test
    void individualEstimateFollowsTheRound5CostFormula() {
        // mandatoryItemsCost = 1*120 = 120, addOnsCost = 1*40 = 40, packagingCost = 60 (preset)
        List<MandatoryItem> mandatory = List.of(item(1, 120));
        List<LineItem> addOns = List.of(lineItem(1, 40, 0.2));
        Packaging packaging = Packaging.builder().presetCost(60).presetTimeHours(0.5).itemizedList(List.of()).build();

        Order.CostEstimate estimate = calc.estimateIndividual(mandatory, addOns, List.of(), packaging, 4.0, 1.0, 0.0,
                0.15, 0.20, 100.0, 0, Instant.parse("2026-01-01T00:00:00Z"), 4.0);

        assertThat(estimate.getMandatoryItemsCost()).isEqualTo(120);
        assertThat(estimate.getAddOnsCost()).isEqualTo(40);
        assertThat(estimate.getPackagingCost()).isEqualTo(60);
        // grossTimeHours = craftingTimeHours(4) + assemblyTimeHours(1) + packagingTime(0.5) = 5.5 —
        // the add-on's own unitTimeHours (0.2) is deliberately NOT counted, materials carry no time.
        assertThat(estimate.getGrossTimeHours()).isCloseTo(5.5, within(1e-9));
        double labor = 5.5 * 100.0; // 550 — round 5: labor is a cost line now, no overhead cost line at all
        assertThat(estimate.getLaborCost()).isCloseTo(labor, within(1e-9));
        double gross = 120 + 40 + 60 + labor; // 770
        double profit = gross * 0.20; // 154
        assertThat(estimate.getGrossCost()).isCloseTo(gross, within(1e-9));
        assertThat(estimate.getProfitAmount()).isCloseTo(profit, within(1e-9));
        assertThat(estimate.getFinalCost()).isCloseTo(gross + profit, within(1e-9));
    }

    @Test
    void individualDueDateRoundsUpFromCreatorHoursPerDay() {
        Packaging packaging = Packaging.builder().presetCost(0).presetTimeHours(0).itemizedList(List.of()).build();
        Instant received = Instant.parse("2026-01-01T00:00:00Z");
        // grossTimeHours = 7h crafting / 4h-per-day = 1.75 -> rounds up to 2 work days; no
        // delivery buffer, no time-overhead percentage -> quotable delivery stays 2 days out
        Order.CostEstimate estimate = calc.estimateIndividual(List.of(), List.of(), List.of(), packaging, 7.0, 0, 0,
                0, 0, 0, 0, received, 4.0);
        assertThat(estimate.getWorkDays()).isEqualTo(2);
        assertThat(estimate.getComputedDueDate()).isEqualTo(received.plusSeconds(2 * 24 * 3600));
    }

    @Test
    void individualResearchTimeAddsExtraDaysToTheDueDate() {
        Packaging packaging = Packaging.builder().presetCost(0).presetTimeHours(0).itemizedList(List.of()).build();
        Instant received = Instant.parse("2026-01-01T00:00:00Z");
        // grossTimeHours = 4h crafting + 0 assembly + 0 packaging + 4h research = 8h / 4h-per-day = 2 days
        Order.CostEstimate estimate = calc.estimateIndividual(List.of(), List.of(), List.of(), packaging, 4.0, 0, 4.0,
                0, 0, 0, 0, received, 4.0);
        assertThat(estimate.getGrossTimeHours()).isCloseTo(8.0, within(1e-9));
        assertThat(estimate.getComputedDueDate()).isEqualTo(received.plusSeconds(2 * 24 * 3600));
    }

    /** End-to-end regression for the round 5 pricing/delivery redesign — same numbers as the
     *  worked "crochet vase with 2 flower types" example agreed with the product owner:
     *  materials ₹425 + add-ons ₹50 + packaging ₹60 + labor (5.5h × ₹100/h = ₹550) = ₹1,085
     *  gross, 20% margin = ₹217 profit, ₹1,302 final. Delivery: 5.5h / 4h-per-day = 2 work
     *  days, + 1 delivery-buffer day = 3, × 1.15 time-overhead = 3.45, rounds up to 4 days. */
    @Test
    void laborCostAndDeliveryFormulaMatchTheWorkedExample() {
        List<MandatoryItem> mandatory = List.of(item(1, 425));
        List<LineItem> addOns = List.of(lineItem(1, 50, null));
        Packaging packaging = Packaging.builder().presetCost(60).presetTimeHours(0.5).itemizedList(List.of()).build();
        Instant received = Instant.parse("2026-01-01T00:00:00Z");

        Order.CostEstimate estimate = calc.estimateIndividual(mandatory, addOns, List.of(), packaging,
                3.5, 1.0, 0.5, 0.15, 0.20, 100.0, 1, received, 4.0);

        assertThat(estimate.getGrossTimeHours()).isCloseTo(5.5, within(1e-9));
        assertThat(estimate.getLaborCost()).isCloseTo(550, within(1e-9));
        double gross = 425 + 50 + 60 + 550; // 1085
        assertThat(estimate.getGrossCost()).isCloseTo(gross, within(1e-9));
        double profit = gross * 0.20; // 217
        assertThat(estimate.getProfitAmount()).isCloseTo(profit, within(1e-9));
        assertThat(estimate.getFinalCost()).isCloseTo(gross + profit, within(1e-9)); // 1302

        assertThat(estimate.getWorkDays()).isEqualTo(2);
        assertThat(estimate.getDeliveryBufferDays()).isEqualTo(1);
        assertThat(estimate.getComputedDueDate()).isEqualTo(received.plusSeconds(4 * 24 * 3600));
    }

    @Test
    void priceComponentAppliesNoOverheadOrProfitAndScalesByQuantity() {
        // materials 1*30 + addOns 1*5 = 35 per unit, crafting 0.75h per unit, quantity 3
        Order.Component lily = Order.Component.builder()
                .componentId("c1").quantity(3).craftingTimeHours(0.75)
                .mandatoryItems(List.of(item(1, 30)))
                .addOns(List.of(lineItem(1, 5, null)))
                .build();

        Order.Component priced = calc.priceComponent(lily);

        assertThat(priced.getPerUnitCost()).isEqualTo(35);
        assertThat(priced.getTotalCost()).isEqualTo(105);
        assertThat(priced.getPerUnitTimeHours()).isEqualTo(0.75);
        assertThat(priced.getTotalTimeHours()).isCloseTo(2.25, within(1e-9));
    }

    @Test
    void estimateIndividualAddsComponentsCostAndTimeOnTopOfTheFlatFields() {
        // base: mandatory 1*100, crafting 2h. Plus one component: materials 1*30, 0.75h/unit, qty 3.
        List<MandatoryItem> mandatory = List.of(item(1, 100));
        Packaging packaging = Packaging.builder().presetCost(0).presetTimeHours(0).itemizedList(List.of()).build();
        Order.Component lily = calc.priceComponent(Order.Component.builder()
                .componentId("c1").quantity(3).craftingTimeHours(0.75)
                .mandatoryItems(List.of(item(1, 30))).addOns(List.of())
                .build());

        Order.CostEstimate estimate = calc.estimateIndividual(mandatory, List.of(), List.of(lily), packaging,
                2.0, 0, 0, 0, 0, 0, 0, Instant.parse("2026-01-01T00:00:00Z"), 4.0);

        // gross = mandatoryItemsCost(100) + componentsCost(3*30=90) = 190
        assertThat(estimate.getMandatoryItemsCost()).isEqualTo(100);
        assertThat(estimate.getGrossCost()).isEqualTo(190);
        // time = craftingTimeHours(2) + componentsTimeHours(3*0.75=2.25) = 4.25
        assertThat(estimate.getGrossTimeHours()).isCloseTo(4.25, within(1e-9));
        assertThat(estimate.getItemizedBreakdown()).anySatisfy(b -> {
            assertThat(b.getLabel()).isEqualTo("Components");
            assertThat(b.getAmount()).isEqualTo(90);
        });
    }

    @Test
    void packagingCostUsesItemizedSumWhenNonEmptyElsePreset() {
        Packaging withPreset = Packaging.builder().presetCost(60).presetTimeHours(0.5).itemizedList(List.of()).build();
        assertThat(withPreset.cost()).isEqualTo(60);

        Packaging itemized = Packaging.builder().presetCost(999).itemizedList(List.of(
                LineItem.builder().name("Kraft box").quantity(1).unitCost(30).unitTimeHours(0.1).build(),
                LineItem.builder().name("Tissue paper").quantity(2).unitCost(5).unitTimeHours(0.02).build()
        )).build();
        assertThat(itemized.cost()).isEqualTo(40); // 30 + 2*5
        assertThat(itemized.timeHours()).isCloseTo(0.14, within(1e-9)); // 0.1 + 2*0.02
    }

    @Test
    void individualCompletionIsEqualWeightedAverageAcrossStages() {
        List<StageAssignment> stages = List.of(
                StageAssignment.builder().stageKey("crocheting").unitsCompleted(1).totalUnits(1).build(),
                StageAssignment.builder().stageKey("assembly").unitsCompleted(0).totalUnits(1).build(),
                StageAssignment.builder().stageKey("packaging").unitsCompleted(0).totalUnits(1).build(),
                StageAssignment.builder().stageKey("shipment").unitsCompleted(0).totalUnits(1).build());
        assertThat(calc.individualCompletionFraction(stages)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void paymentStatusFollowsSpecFormula() {
        assertThat(calc.derivePaymentStatus(List.of(), 1000)).isEqualTo(PaymentStatus.UNPAID);

        List<PaymentEntry> partial = List.of(payment(PaymentType.ADVANCE, 400));
        assertThat(calc.derivePaymentStatus(partial, 1000)).isEqualTo(PaymentStatus.PARTIALLY_PAID);

        List<PaymentEntry> full = List.of(payment(PaymentType.ADVANCE, 400), payment(PaymentType.FINAL, 600));
        assertThat(calc.derivePaymentStatus(full, 1000)).isEqualTo(PaymentStatus.PAID_IN_FULL);

        List<PaymentEntry> refundedExceeds = List.of(payment(PaymentType.ADVANCE, 400), payment(PaymentType.REFUND, 500));
        assertThat(calc.derivePaymentStatus(refundedExceeds, 1000)).isEqualTo(PaymentStatus.REFUNDED);

        // paid in full then fully refunded nets to exactly zero -- still distinguishable from
        // "never paid" because a REFUND entry exists (platform integration decision)
        List<PaymentEntry> fullyRefunded = List.of(payment(PaymentType.FINAL, 1000), payment(PaymentType.REFUND, 1000));
        assertThat(calc.derivePaymentStatus(fullyRefunded, 1000)).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void anyPositivePaymentIsPaidInFullAgainstAZeroCostEstimate() {
        // an order with nothing itemized yet has finalCost == 0 -- any real payment must
        // still resolve to PAID_IN_FULL (net >= finalCost), not get stuck as PARTIALLY_PAID
        // forever because of a naive "finalCost > 0" guard
        List<PaymentEntry> advance = List.of(payment(PaymentType.ADVANCE, 100));
        assertThat(calc.derivePaymentStatus(advance, 0)).isEqualTo(PaymentStatus.PAID_IN_FULL);
        assertThat(calc.derivePaymentStatus(List.of(), 0)).isEqualTo(PaymentStatus.UNPAID);
    }

    private PaymentEntry payment(PaymentType type, double amount) {
        return PaymentEntry.builder().paymentId("p").type(type).amount(EncryptedString.of(Double.toString(amount)))
                .date(Instant.now()).build();
    }

    @Test
    void bulkVariantPricingMultipliesPerUnitBySpecifiedQuantity() {
        Variant variant = Variant.builder()
                .variantId("v1").label("Blue flower").quantity(26)
                .mandatoryItems(List.of(item(1, 100))) // per unit
                .addOns(List.of())
                .packaging(Packaging.builder().presetCost(20).presetTimeHours(0.1).itemizedList(List.of()).build())
                .craftingTimeHours(1.2)
                .assemblyTimeHours(0.3)
                .splitAllocation(List.of())
                .build();

        Variant priced = calc.priceVariant(variant, 0.20, 0);

        double gross = 100 + 0 + 20; // 120, no labor since hourlyWage = 0 here
        double profit = gross * 0.20;
        double perUnitCost = gross + profit;
        assertThat(priced.getPerUnitCost()).isCloseTo(perUnitCost, within(1e-9));
        assertThat(priced.getTotalCost()).isCloseTo(perUnitCost * 26, within(1e-9));
        assertThat(priced.getPerUnitTimeHours()).isCloseTo(1.2 + 0.3 + 0.1, within(1e-9));
        assertThat(priced.getTotalTimeHours()).isCloseTo((1.2 + 0.3 + 0.1) * 26, within(1e-9));
    }

    /** Regression test for a real gap the round 4 review found: estimateIndividual's
     *  components-on-top-of-flat-fields additivity was tested, but priceVariant's — the
     *  bulk path, which folds a *variant's own* components in exactly the same way — never
     *  was. Verifies a bulk variant with one component priced per unit still multiplies out
     *  correctly by the variant's own quantity, on top of the variant's flat mandatory
     *  items/crafting time, with overhead/profit applied exactly once on the combined
     *  per-unit total (never inside priceComponent itself). */
    @Test
    void bulkVariantPricingFoldsInAComponentsCostAndTimeOnTopOfTheFlatFields() {
        Order.Component vaseBase = Order.Component.builder()
                .componentId("c1").quantity(1).craftingTimeHours(2.5)
                .mandatoryItems(List.of(item(1, 150))).addOns(List.of())
                .build();
        Variant variant = Variant.builder()
                .variantId("v1").label("Spring vase").quantity(4)
                .mandatoryItems(List.of()) // fully decomposed into components for this variant
                .addOns(List.of())
                .components(List.of(vaseBase))
                .packaging(Packaging.builder().itemizedList(List.of()).build())
                .craftingTimeHours(0)
                .assemblyTimeHours(1.5)
                .splitAllocation(List.of())
                .build();

        Variant priced = calc.priceVariant(variant, 0.20, 0);

        // componentCost = 1*150 = 150; gross = mandatory(0) + components(150) + addOns(0) + packaging(0) = 150
        double gross = 150;
        double profit = gross * 0.20;
        double perUnitCost = gross + profit;
        assertThat(priced.getPerUnitCost()).isCloseTo(perUnitCost, within(1e-9));
        assertThat(priced.getTotalCost()).isCloseTo(perUnitCost * 4, within(1e-9));
        // componentTime = 1*2.5 = 2.5; perUnitTime = crafting(0) + componentsTime(2.5) + assembly(1.5) + packaging(0)
        double perUnitTimeHours = 0 + 2.5 + 1.5 + 0;
        assertThat(priced.getPerUnitTimeHours()).isCloseTo(perUnitTimeHours, within(1e-9));
        assertThat(priced.getTotalTimeHours()).isCloseTo(perUnitTimeHours * 4, within(1e-9));
        // the component itself carries no overhead/profit — only its raw scaled cost/time
        assertThat(priced.getComponents().get(0).getTotalCost()).isEqualTo(150);
        assertThat(priced.getComponents().get(0).getTotalTimeHours()).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void bulkDueDateIsDrivenByTheSlowestLoadedCreatorAcrossAllTheirVariants() {
        Variant v1 = calc.priceVariant(Variant.builder().variantId("v1").quantity(20)
                .craftingTimeHours(1).packaging(Packaging.builder().itemizedList(List.of()).build())
                .splitAllocation(List.of(SplitLine.builder().creatorId("A").quantityAssigned(20).build()))
                .build(), 0, 0);
        Variant v2 = calc.priceVariant(Variant.builder().variantId("v2").quantity(5)
                .craftingTimeHours(1).packaging(Packaging.builder().itemizedList(List.of()).build())
                .splitAllocation(List.of(SplitLine.builder().creatorId("B").quantityAssigned(5).build()))
                .build(), 0, 0);

        double hoursA = calc.creatorTotalHours("A", List.of(v1, v2)); // 20 * 1h = 20h
        double hoursB = calc.creatorTotalHours("B", List.of(v1, v2)); // 5 * 1h = 5h
        assertThat(hoursA).isEqualTo(20);
        assertThat(hoursB).isEqualTo(5);

        Instant received = Instant.parse("2026-01-01T00:00:00Z");
        // A: 20h/4h-per-day = 5 days; B: 5h/8h-per-day = 1 day -> driven by A, no buffer
        Instant due = calc.computeBulkDueDate(received,
                List.of(new CreatorWorkload(hoursA, 4), new CreatorWorkload(hoursB, 8)), 0, 0, 0);
        assertThat(due).isEqualTo(received.plusSeconds(5 * 24 * 3600));
    }

    @Test
    void bulkDueDateAddsTheLogisticsBufferWhenPresent() {
        Instant received = Instant.parse("2026-01-01T00:00:00Z");
        Instant due = calc.computeBulkDueDate(received, List.of(new CreatorWorkload(20, 4)), 2, 0, 0);
        assertThat(due).isEqualTo(received.plusSeconds(7 * 24 * 3600)); // 5 days + 2-day buffer
    }

    @Test
    void bulkCompletionAveragesSplitTrackedAndBatchTrackedStages() {
        List<WorkStageType> stages = List.of(
                new WorkStageType("crocheting", "Crocheting", 1, true),
                new WorkStageType("packaging", "Packaging", 2, false));

        Variant variant = Variant.builder().variantId("v1").quantity(10)
                .splitAllocation(List.of(
                        SplitLine.builder().creatorId("A").quantityAssigned(6)
                                .stageProgress(List.of(StageProgressEntry.builder().stageKey("crocheting").unitsCompleted(6).build()))
                                .build(),
                        SplitLine.builder().creatorId("B").quantityAssigned(4)
                                .stageProgress(List.of(StageProgressEntry.builder().stageKey("crocheting").unitsCompleted(0).build()))
                                .build()))
                .build();

        BulkDetails details = BulkDetails.builder()
                .variants(List.of(variant))
                .totalQuantity(10)
                .stageProgress(List.of(StageProgress.builder().stageKey("packaging").unitsCompleted(0).totalUnits(10).build()))
                .build();

        // crocheting: 6/10 = 60% split-tracked; packaging: 0/10 = 0% batch-tracked; average = 30%
        assertThat(calc.bulkCompletionFraction(details, stages)).isCloseTo(0.30, within(1e-9));
    }
}
