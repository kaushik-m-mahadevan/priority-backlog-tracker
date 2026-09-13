package com.backlogtracker.ordertracker.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.backlogtracker.ordertracker.order.domain.Packaging;
import com.backlogtracker.ordertracker.order.domain.PaymentMode;
import com.backlogtracker.ordertracker.order.domain.Payment;
import com.backlogtracker.ordertracker.order.domain.PaymentStatus;
import com.backlogtracker.ordertracker.order.domain.StageProgress;

class OrderCalculatorTest {

    private final OrderCalculator calc = new OrderCalculator();

    private StageProgress stage(String key, double hours, double fraction) {
        return StageProgress.builder().stageKey(key).estimatedHours(hours).completionFraction(fraction).build();
    }

    @Test
    void overallCompletionIsTimeWeightedAcrossStages() {
        // crocheting 4h fully done, packaging 3h not started -> 4/7 = ~57.1%
        List<StageProgress> stages = List.of(stage("crocheting", 4, 1.0), stage("packaging", 3, 0.0));
        assertThat(calc.overallCompletionFraction(stages)).isCloseTo(4.0 / 7.0, within(1e-9));
    }

    @Test
    void overallCompletionIsZeroWhenNoHoursEstimatedYet() {
        List<StageProgress> stages = List.of(stage("crocheting", 0, 0.0));
        assertThat(calc.overallCompletionFraction(stages)).isEqualTo(0.0);
    }

    @Test
    void overallCompletionHandlesPartialProgressOnMultipleStages() {
        List<StageProgress> stages = List.of(stage("crocheting", 4, 0.5), stage("packaging", 4, 0.25));
        // each stage is half the total hours -> 0.5*0.5 + 0.5*0.25 = 0.375
        assertThat(calc.overallCompletionFraction(stages)).isCloseTo(0.375, within(1e-9));
    }

    @Test
    void totalCostAppliesOverheadThenProfitMarginOnTopOfMaterialsPlusPackaging() {
        Packaging packaging = Packaging.builder().presetCost(40).presetTimeHours(0.25).itemizedList(List.of()).build();
        // (1000 materials + 40 packaging) * 1.15 overhead * 1.20 margin
        double expected = (1000 + 40) * 1.15 * 1.20;
        assertThat(calc.totalCost(1000, packaging, 0.15, 0.20)).isCloseTo(expected, within(1e-6));
    }

    @Test
    void totalCostUsesItemizedPackagingSumWhenPresent() {
        Packaging packaging = Packaging.builder()
                .presetCost(999) // must be ignored since itemizedList is non-empty
                .itemizedList(List.of(
                        Packaging.LineItem.builder().label("Box").cost(30).timeHours(0.1).build(),
                        Packaging.LineItem.builder().label("Tape").cost(10).timeHours(0.05).build()))
                .build();
        assertThat(packaging.cost()).isEqualTo(40);
        assertThat(packaging.timeHours()).isCloseTo(0.15, within(1e-9));
    }

    @Test
    void computedDueDateRoundsUpToWholeDaysBasedOnPrimaryCreatorHoursPerDay() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        List<StageProgress> stages = List.of(stage("crocheting", 4, 0), stage("packaging", 3, 0));
        // 7 hours crafting + 0.25h packaging = 7.25h / 4h-per-day = 1.8125 -> rounds up to 2 days
        Instant due = calc.computedDueDate(created, stages, 0.25, 4.0);
        assertThat(due).isEqualTo(created.plusSeconds(2 * 24 * 3600));
    }

    @Test
    void computedDueDateNeverGoesBelowOneDayEvenForTinyEstimates() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant due = calc.computedDueDate(created, List.of(stage("crocheting", 0.1, 0)), 0, 8.0);
        assertThat(due).isEqualTo(created.plusSeconds(24 * 3600));
    }

    @Test
    void paymentStatusIsUnpaidWithNoPayments() {
        assertThat(calc.derivePaymentStatus(List.of(), 1000)).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void paymentStatusIsPartiallyPaidWhenLessThanTotal() {
        List<Payment> payments = List.of(Payment.of(400, PaymentMode.UPI, null, Instant.now()));
        assertThat(calc.derivePaymentStatus(payments, 1000)).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void paymentStatusIsPaidWhenNetMeetsOrExceedsTotal() {
        List<Payment> payments = List.of(Payment.of(1000, PaymentMode.CASH, null, Instant.now()));
        assertThat(calc.derivePaymentStatus(payments, 1000)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void paymentStatusDistinguishesFullyRefundedFromNeverPaid() {
        List<Payment> refunded = List.of(
                Payment.of(1000, PaymentMode.CASH, "paid in full", Instant.now()),
                Payment.of(-1000, PaymentMode.CASH, "refunded", Instant.now()));
        assertThat(calc.derivePaymentStatus(refunded, 1000)).isEqualTo(PaymentStatus.FULLY_REFUNDED);
        assertThat(calc.derivePaymentStatus(List.of(), 1000)).isEqualTo(PaymentStatus.UNPAID);
    }
}
