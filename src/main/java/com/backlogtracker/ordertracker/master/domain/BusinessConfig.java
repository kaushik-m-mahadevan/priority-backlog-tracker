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

    /** {@code isTool} distinguishes a reusable tool (e.g. a crochet hook — not purchased
     *  per order, can appear multiple times on one order with no cost implication) from a
     *  material (e.g. wool — cost/quantity matter, and a project may still use several
     *  entries of it, e.g. two colours). Order-level entries always allow multiple values
     *  per type regardless of this flag; the flag only decides whether quantity/unit cost
     *  are meaningful to collect for that type. */
    public record MandatoryItemType(String itemKey, String label, List<String> allowedValues, boolean isTool) {
    }

    /**
     * sequenceOrder is display/workflow order only. {@code splitTracked} distinguishes how
     * a bulk order tracks progress on this stage (spec §5.11): {@code true} for a stage
     * naturally split by creator (e.g. Crocheting, Assembly) — progress lives per creator
     * per variant; {@code false} for a stage typically done once across the whole batch
     * (e.g. Packaging, Shipment) — progress lives once at the order level. Individual-order
     * completion is an equal-weighted average across all stages (spec §5.11's stated
     * default) regardless of this flag.
     */
    public record WorkStageType(String stageKey, String label, int sequenceOrder, boolean splitTracked) {
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
                        new MandatoryItemType("wool", "Wool", null, false),
                        new MandatoryItemType("needle", "Needle", null, true))))
                .workStages(new ArrayList<>(List.of(
                        new WorkStageType("crocheting", "Crocheting", 1, true),
                        new WorkStageType("assembly", "Assembly", 2, true),
                        new WorkStageType("packaging", "Packaging", 3, false),
                        new WorkStageType("shipment", "Shipment", 4, false))))
                .build();
    }
}
