package com.backlogtracker.backlogtracker.item.domain;

/** Effort estimate units (design §14). No fractions or decimals anywhere. */
public enum EffortUnit {
    MINUTES(1),
    HOURS(60),
    DAYS(60 * 24);

    private final int minutesPerUnit;

    EffortUnit(int minutesPerUnit) {
        this.minutesPerUnit = minutesPerUnit;
    }

    public long toMinutes(int value) {
        return (long) value * minutesPerUnit;
    }
}
