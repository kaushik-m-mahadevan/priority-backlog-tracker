package com.backlogtracker.materialinventory.yarn.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A canonical yarn variant for one business, identified by brand + thickness + colour
 * together (design decision) — the same three attributes anyone in the business would use
 * to tell two skeins apart. Business-wide: any member can create or edit one (design
 * decision: any business member), and every member's personal inventory entries
 * ({@code InventoryEntry}) reference this shared identity rather than each re-describing
 * their own yarn in free text.
 */
@Document("materialInventoryYarnTypes")
@CompoundIndex(name = "group_identity", def = "{'groupId': 1, 'brand': 1, 'thickness': 1, 'colour': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YarnType {

    @Id
    private String id;

    private String groupId;

    private String brand;
    private String thickness;
    private String colour;

    /** Fiber content, e.g. "100% cotton" or "acrylic/wool blend" — descriptive only, not
     *  part of the identity (two batches of the same brand/thickness/colour are the same
     *  yarn type even if a label wording differs slightly). */
    private String material;
    /** Per-skein weight in grams, as printed on the label. Null when unknown. */
    private Double skeinWeightGrams;
    /** Per-skein length in meters, as printed on the label. Null when unknown. */
    private Double skeinLengthMeters;
    /** Free text, e.g. "4mm / US H-8" — the label's own suggestion, not a business rule. */
    private String recommendedHookSize;

    /** Free-text extra detail (dye lot, texture) that doesn't affect identity. */
    private String notes;

    /** What was last paid per skein. Null when never recorded. */
    private Double costPerSkein;

    /** Every change to {@link #costPerSkein} (including the first time it's set), oldest
     *  first — so a price trend is visible directly on the yarn type itself rather than
     *  needing a separate ledger lookup. */
    @Builder.Default
    private List<CostChange> costHistory = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostChange {
        /** Null for the very first recorded cost. */
        private Double previousCost;
        private Double newCost;
        private Instant changedAt;
    }
}
