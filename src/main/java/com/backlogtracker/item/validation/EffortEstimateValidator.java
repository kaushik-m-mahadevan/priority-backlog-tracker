package com.backlogtracker.item.validation;

import java.util.Set;

import com.backlogtracker.item.domain.EffortEstimate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EffortEstimateValidator
        implements ConstraintValidator<ValidEffortEstimate, EffortEstimate> {

    private static final Set<Integer> ALLOWED_MINUTES = Set.of(15, 30, 45);

    @Override
    public boolean isValid(EffortEstimate e, ConstraintValidatorContext ctx) {
        if (e == null) {
            return true; // @NotNull handles presence
        }
        if (e.getUnit() == null) {
            return false;
        }
        int v = e.getValue();
        return switch (e.getUnit()) {
            case MINUTES -> ALLOWED_MINUTES.contains(v);
            case HOURS -> v >= 1 && v <= 23;
            case DAYS -> v >= 1 && v <= 30;
        };
    }
}
