package com.backlogtracker.backlogtracker.config.service;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The §2 safe-removal rule shared by {@link ConfigService#removePriority} and
 * {@link GroupCategoryService#removeCategory} (bdup-3): more than one item uses the value
 * being removed → blocked (409, lists up to 8 affected titles); exactly one → a valid
 * {@code reassignTo} is required and returned for the caller to apply; none → nothing
 * further needed, returns {@code null}. Deliberately doesn't touch persistence or the
 * affected-items query itself — those differ per caller (global vs per-group scope,
 * different repositories) and stay in each service.
 */
final class SafeRemovalRule {

    private SafeRemovalRule() {
    }

    static String requireNonBlank(String s, String what) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("A " + what + " name is required");
        }
        return s.trim();
    }

    static void requireAtLeastOneRemains(int currentSize, String what) {
        if (currentSize <= 1) {
            throw new IllegalArgumentException("At least one " + what + " must remain");
        }
    }

    /** {@code affectedTitles} is a supplier so the (potentially non-trivial) titles query
     *  only ever runs when there's actually a conflict to report. */
    static String checkUsageAndValidateReassign(long usageCount, Supplier<List<String>> affectedTitles,
                                                String reassignTo, List<String> allowedValues,
                                                String removingValue, String what) {
        if (usageCount > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    usageCount + " items use the " + what + " '" + removingValue + "' — reassign them first: "
                            + affectedTitles.get());
        }
        if (usageCount == 1) {
            if (reassignTo == null || reassignTo.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "One item uses this " + what + " — supply reassignTo to move it first");
            }
            if (reassignTo.equals(removingValue) || !allowedValues.contains(reassignTo)) {
                throw new IllegalArgumentException("reassignTo must be another existing " + what);
            }
            return reassignTo;
        }
        return null;
    }
}
