package com.backlogtracker.backlogtracker.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.backlogtracker.item.validation.EffortEstimateValidator;

class EffortEstimateValidatorTest {

    private final EffortEstimateValidator validator = new EffortEstimateValidator();

    private boolean valid(int value, EffortUnit unit) {
        return validator.isValid(new EffortEstimate(value, unit), null);
    }

    @ParameterizedTest
    @CsvSource({
            "15,MINUTES", "30,MINUTES", "45,MINUTES",
            "1,HOURS", "23,HOURS",
            "1,DAYS", "30,DAYS"
    })
    void acceptsAllowedCombinations(int value, EffortUnit unit) {
        assertThat(valid(value, unit)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0,MINUTES", "10,MINUTES", "20,MINUTES", "60,MINUTES",
            "0,HOURS", "24,HOURS",
            "0,DAYS", "31,DAYS", "-1,DAYS"
    })
    void rejectsDisallowedCombinations(int value, EffortUnit unit) {
        assertThat(valid(value, unit)).isFalse();
    }

    @Test
    void nullEstimateIsLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void nullUnitIsInvalid() {
        assertThat(validator.isValid(new EffortEstimate(15, null), null)).isFalse();
    }

    @Test
    void toMinutesConverts() {
        assertThat(new EffortEstimate(45, EffortUnit.MINUTES).toMinutes()).isEqualTo(45);
        assertThat(new EffortEstimate(2, EffortUnit.HOURS).toMinutes()).isEqualTo(120);
        assertThat(new EffortEstimate(3, EffortUnit.DAYS).toMinutes()).isEqualTo(3 * 1440);
    }
}
