package com.backlogtracker.item.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Enforces the design §14 value/unit rules on an {@code EffortEstimate}. */
@Documented
@Constraint(validatedBy = EffortEstimateValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidEffortEstimate {

    String message() default
            "effort must be 15/30/45 minutes, 1-23 hours, or 1-30 days";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
