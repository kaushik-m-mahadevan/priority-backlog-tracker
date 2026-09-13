package com.backlogtracker.backlogtracker.config.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Editable ranking configuration (design §7). Categories and priority labels are
 * changed through their own add/remove endpoints (safety rules in §2), not here.
 */
public record UpdateConfigRequest(
        @NotNull Double priorityWeight,
        @NotNull Double urgencyWeight,
        @NotNull Double effortWeight,
        @Positive int urgencyWindowDays,
        @Positive int staleThresholdDays,
        @Positive int buriedThresholdDays,
        @Positive int defaultDueDateOffsetDays,
        @Positive int effortCapDays,
        @Positive int maxGroupsPerUser,
        @NotNull List<String> buriedPriorityLevels,
        @NotEmpty Map<String, Integer> priorityValues) {
}
