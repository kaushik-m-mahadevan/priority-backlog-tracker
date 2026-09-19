package com.backlogtracker.ordertracker.master.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.backlogtracker.ordertracker.order.domain.DeliveryTier;

/** Pure unit coverage for BusinessConfig's own math — previously only ever proven
 *  indirectly through full order-creation integration tests, with 3 of the 4 DeliveryTier
 *  values never directly exercised and the "configured value is 0, fall back to the
 *  built-in default" branch never hit at all. */
class BusinessConfigTest {

    @Test
    void bufferDaysForUsesTheConfiguredValuePerTierWhenSet() {
        BusinessConfig cfg = BusinessConfig.builder()
                .deliveryBufferSameCityDays(2)
                .deliveryBufferSameStateDays(4)
                .deliveryBufferOtherStateDays(6)
                .deliveryBufferInternationalDays(10)
                .build();

        assertThat(cfg.bufferDaysFor(DeliveryTier.SAME_CITY)).isEqualTo(2);
        assertThat(cfg.bufferDaysFor(DeliveryTier.SAME_STATE)).isEqualTo(4);
        assertThat(cfg.bufferDaysFor(DeliveryTier.OTHER_STATE)).isEqualTo(6);
        assertThat(cfg.bufferDaysFor(DeliveryTier.INTERNATIONAL)).isEqualTo(10);
    }

    /** A document persisted before these fields existed hydrates every int field to 0
     *  (Mongo/Java primitive default) — bufferDaysFor must fall back to the built-in
     *  sensible default per tier rather than quoting a 0-day delivery buffer. */
    @Test
    void bufferDaysForFallsBackToTheBuiltInDefaultWhenConfiguredValueIsZero() {
        BusinessConfig cfg = BusinessConfig.builder().build(); // every buffer field defaults to 0

        assertThat(cfg.bufferDaysFor(DeliveryTier.SAME_CITY)).isEqualTo(1);
        assertThat(cfg.bufferDaysFor(DeliveryTier.SAME_STATE)).isEqualTo(2);
        assertThat(cfg.bufferDaysFor(DeliveryTier.OTHER_STATE)).isEqualTo(3);
        assertThat(cfg.bufferDaysFor(DeliveryTier.INTERNATIONAL)).isEqualTo(5);
    }

    /** A business that genuinely wants a 0-day buffer for local same-city drop-offs has no
     *  way to express that today (0 is indistinguishable from "never configured") — a real
     *  limitation, not covered here since it's the existing, deliberate design, but worth
     *  knowing this test documents that boundary rather than a bug. */
    @Test
    void negativeConfiguredValuesAlsoFallBackToTheDefaultLikeZeroDoes() {
        BusinessConfig cfg = BusinessConfig.builder().deliveryBufferSameCityDays(-1).build();
        assertThat(cfg.bufferDaysFor(DeliveryTier.SAME_CITY)).isEqualTo(1);
    }

    @Test
    void effectiveHourlyWageUsesTheDefaultUntilConfirmed() {
        BusinessConfig unconfirmed = BusinessConfig.builder()
                .hourlyWage(250.0).hourlyWageConfirmed(false).build();
        assertThat(unconfirmed.effectiveHourlyWage()).isEqualTo(BusinessConfig.DEFAULT_HOURLY_WAGE);

        BusinessConfig confirmed = BusinessConfig.builder()
                .hourlyWage(250.0).hourlyWageConfirmed(true).build();
        assertThat(confirmed.effectiveHourlyWage()).isEqualTo(250.0);
    }

    @Test
    void setupCompleteOrDefaultTreatsAMissingFlagAsComplete() {
        BusinessConfig noFlag = BusinessConfig.builder().build();
        assertThat(noFlag.setupCompleteOrDefault()).isTrue();

        BusinessConfig incomplete = BusinessConfig.builder().setupComplete(false).build();
        assertThat(incomplete.setupCompleteOrDefault()).isFalse();

        BusinessConfig complete = BusinessConfig.builder().setupComplete(true).build();
        assertThat(complete.setupCompleteOrDefault()).isTrue();
    }
}
