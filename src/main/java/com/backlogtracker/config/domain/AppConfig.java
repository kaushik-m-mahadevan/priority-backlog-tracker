package com.backlogtracker.config.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-document application configuration (see docs/design.md §2, §7).
 * Always stored with a fixed id so there is exactly one config row.
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

    /** Ordered list of valid category labels. */
    private List<String> categories;

    private int defaultDueDateOffsetDays;
    private int effortCapDays;

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
                .priorities(List.of("Critical", "High", "Medium", "Low"))
                .priorityWeight(0.333)
                .urgencyWeight(0.333)
                .effortWeight(0.334)
                .urgencyWindowDays(14)
                .staleThresholdDays(14)
                .buriedThresholdDays(30)
                .buriedPriorityLevels(List.of("Low"))
                .categories(List.of(
                        "Research", "Skill-Building", "Project",
                        "Technical Discussion", "Admin-Ops", "Other"))
                .defaultDueDateOffsetDays(30)
                .effortCapDays(30)
                .build();
    }
}
