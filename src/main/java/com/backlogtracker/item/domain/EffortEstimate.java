package com.backlogtracker.item.domain;

import com.backlogtracker.item.validation.ValidEffortEstimate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embedded value/unit effort estimate (design §14). Allowed combinations:
 * minutes ∈ {15, 30, 45}; hours ∈ 1..23; days ∈ 1..30.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ValidEffortEstimate
public class EffortEstimate {

    private int value;
    private EffortUnit unit;

    public long toMinutes() {
        return unit.toMinutes(value);
    }
}
