package com.backlogtracker.ordertracker.master.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Assembly &amp; packaging preset for one group's business (mb-4) — a rough cost/time
 *  template (e.g. "Gift-wrapped box assembly") a member can pick on an order so a
 *  reasonable estimate gets pulled in automatically, on top of the order's own free-text
 *  {@code assemblyPackagingInstructions} notes (additive, not a replacement for them).
 *  Deliberately its own document/collection rather than reusing {@link PresetOption} —
 *  same one-class-per-concept convention as {@link ShippingLanePreset}/{@link ComponentTemplate},
 *  even though the shape is identical. */
@Document("orderTrackerAssemblyPresets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyPreset {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private String label;
    private double estimatedCost;
    private double estimatedTimeHours;
}
