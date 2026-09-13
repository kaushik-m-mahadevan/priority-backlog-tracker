package com.backlogtracker.ordertracker.master.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One group's own Order Tracker business configuration (design §2) — keyed by groupId
 * itself (one document per group, not a fixed singleton id like Backlog Tracker's
 * AppConfig), since every group is its own separate business (platform integration
 * decision): mandatory item types and work stages are exactly the kind of thing a
 * different business reconfigures without a schema change.
 */
@Document("orderTrackerBusinessConfig")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfig {

    @Id
    private String groupId;

    private double overheadPercentage;
    private double profitMarginPercentage;
    private String currency;

    /** 2-digit, auto-assigned, guaranteed different from {@link #bulkOrderTypeCode}. */
    private String individualOrderTypeCode;
    /** 2-digit, auto-assigned, guaranteed different from {@link #individualOrderTypeCode}. */
    private String bulkOrderTypeCode;

    private List<MandatoryItemType> mandatoryItemTypes;
    private List<WorkStageType> workStages;

    public record MandatoryItemType(String itemKey, String label, List<String> allowedValues) {
    }

    /** No weight field — stage-completion weighting is derived per-order from each
     *  stage's own estimated hours (platform integration decision), not a fixed number
     *  configured here. sequenceOrder is display/workflow order only. */
    public record WorkStageType(String stageKey, String label, int sequenceOrder) {
    }

    /** Starting point for a newly configured business — the crochet shop from the spec.
     *  Order-type codes are assigned separately (need the uniqueness check), not here. */
    public static BusinessConfig defaultsFor(String groupId) {
        return BusinessConfig.builder()
                .groupId(groupId)
                .overheadPercentage(0.15)
                .profitMarginPercentage(0.20)
                .currency("INR")
                .mandatoryItemTypes(new ArrayList<>(List.of(
                        new MandatoryItemType("wool", "Wool", null),
                        new MandatoryItemType("needle", "Needle", null))))
                .workStages(new ArrayList<>(List.of(
                        new WorkStageType("crocheting", "Crocheting", 1),
                        new WorkStageType("assembly", "Assembly", 2),
                        new WorkStageType("packaging", "Packaging", 3),
                        new WorkStageType("shipment", "Shipment", 4))))
                .build();
    }
}
