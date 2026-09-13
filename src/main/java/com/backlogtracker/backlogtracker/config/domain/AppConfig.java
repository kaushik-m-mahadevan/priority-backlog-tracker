package com.backlogtracker.backlogtracker.config.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-document application configuration (see docs/design.md §2, §7).
 * Always stored with a fixed id so there is exactly one config row.
 *
 * <p>Categories are NOT here — they live in {@code GroupCategories}, one document per
 * group, since what a finance tracker and a personal to-do list are tracking has nothing
 * in common. {@code maxGroupsPerUser} isn't here either — it moved to the platform-wide
 * {@link com.backlogtracker.commons.group.domain.PlatformConfig}, since a group cap is a
 * commons/platform concept, not part of this applet's own ranking formula. Priorities
 * and the weights/thresholds below are still shared across every group.
 */
@Document("config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppConfig {

    public static final String SINGLETON_ID = "app-config";

    @Id
    private String id;

    /** Priority label -> numeric weight. Ordered high-to-low for display. */
    private Map<String, Integer> priorityValues;

    /** Ordered list of valid priority labels. */
    private List<String> priorities;

    private double priorityWeight;
    private double urgencyWeight;
    private double effortWeight;

    private int urgencyWindowDays;
    private int staleThresholdDays;
    private int buriedThresholdDays;

    /** Priority labels considered "buried" when they linger in the backlog (§5). */
    private List<String> buriedPriorityLevels;

    private int defaultDueDateOffsetDays;
    private int effortCapDays;

    /** Not persisted here — joined in from the platform-wide {@code PlatformConfig} at
     *  read time by {@code ConfigService}, purely so the existing single `/api/config`
     *  contract (and the Settings screen built on it) doesn't have to change. */
    @Transient
    private int maxGroupsPerUser;

    /**
     * The default configuration from docs/design.md §2. Weights are an even three-way
     * split (0.333 / 0.333 / 0.334) that sums to exactly 1.0.
     */
    public static AppConfig defaults() {
        Map<String, Integer> priorityValues = new LinkedHashMap<>();
        priorityValues.put("Critical", 4);
        priorityValues.put("High", 3);
        priorityValues.put("Medium", 2);
        priorityValues.put("Low", 1);

        return AppConfig.builder()
                .id(SINGLETON_ID)
                .priorityValues(priorityValues)
                .priorities(new ArrayList<>(List.of("Critical", "High", "Medium", "Low")))
                .priorityWeight(0.333)
                .urgencyWeight(0.333)
                .effortWeight(0.334)
                .urgencyWindowDays(14)
                .staleThresholdDays(14)
                .buriedThresholdDays(30)
                .buriedPriorityLevels(new ArrayList<>(List.of("Low")))
                .defaultDueDateOffsetDays(30)
                .effortCapDays(30)
                .build();
    }
}
