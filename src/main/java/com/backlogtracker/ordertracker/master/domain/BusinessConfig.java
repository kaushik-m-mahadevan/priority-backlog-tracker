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
 * decision): work stages are exactly the kind of thing a different business reconfigures
 * without a schema change. Mandatory item types used to be similarly configurable but
 * were descoped to exactly two fixed kinds — see
 * {@link com.backlogtracker.ordertracker.order.domain.Order.MaterialKind}.
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

    /** True unless a business is actively going through the first-time setup wizard
     *  (design decision: forced setup only for newly-created businesses, never a
     *  retroactive prompt for ones that already existed). {@code defaultsFor} always
     *  seeds this true — a business only ever gets {@code false} via
     *  {@link com.backlogtracker.ordertracker.master.service.BusinessConfigService#startSetup},
     *  called by the frontend immediately after creating a new business, right before it
     *  routes into the wizard. Boxed so a document persisted before this field existed
     *  (every pre-existing business) hydrates as null and is treated as complete, not
     *  incomplete — see {@link WorkStageType#splitTracked}'s compact constructor for the
     *  same Mongo-missing-field pattern. */
    private Boolean setupComplete;

    /** Not named getSetupComplete()/isSetupComplete() on purpose — either would collide
     *  with Lombok's own generated accessor for the {@code setupComplete} field and break
     *  Jackson's bean introspection ("Conflicting getter definitions"). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean setupCompleteOrDefault() {
        return setupComplete == null || setupComplete;
    }

    /** 2-digit, auto-assigned, guaranteed different from {@link #bulkOrderTypeCode}. */
    private String individualOrderTypeCode;
    /** 2-digit, auto-assigned, guaranteed different from {@link #individualOrderTypeCode}. */
    private String bulkOrderTypeCode;

    private List<WorkStageType> workStages;

    /**
     * sequenceOrder is display/workflow order only. {@code splitTracked} distinguishes how
     * a bulk order tracks progress on this stage (spec §5.11): {@code true} for a stage
     * naturally split by creator (e.g. Crocheting, Assembly) — progress lives per creator
     * per variant; {@code false} for a stage typically done once across the whole batch
     * (e.g. Packaging, Shipment) — progress lives once at the order level. Individual-order
     * completion is an equal-weighted average across all stages (spec §5.11's stated
     * default) regardless of this flag.
     */
    public record WorkStageType(String stageKey, String label, int sequenceOrder, Boolean splitTracked) {
        /** Boxed so Spring Data can hydrate documents persisted before this field existed —
         *  a primitive component can't bind a missing/null Mongo value, and every config
         *  document persisted before this field existed has no splitTracked key at all. A
         *  blanket false default here would be wrong (not just conservative) — it would
         *  silently flip Crocheting/Assembly to batch-tracked and corrupt every
         *  existing bulk order's completion %. Work stages have no edit UI (frontend only
         *  ever displays workStages, never edits them), so every document's set is exactly
         *  {@link #defaultsFor}'s four — safe to restore the intended value by stageKey. */
        public WorkStageType {
            if (splitTracked == null) {
                splitTracked = "crocheting".equals(stageKey) || "assembly".equals(stageKey);
            }
        }
    }

    /** Starting point for a newly configured business — the crochet shop from the spec.
     *  Order-type codes are assigned separately (need the uniqueness check), not here. */
    public static BusinessConfig defaultsFor(String groupId) {
        return BusinessConfig.builder()
                .groupId(groupId)
                .overheadPercentage(0.15)
                .profitMarginPercentage(0.20)
                .currency("INR")
                .setupComplete(true)
                .workStages(new ArrayList<>(List.of(
                        new WorkStageType("crocheting", "Crocheting", 1, true),
                        new WorkStageType("assembly", "Assembly", 2, true),
                        new WorkStageType("packaging", "Packaging", 3, false),
                        new WorkStageType("shipment", "Shipment", 4, false))))
                .build();
    }
}
