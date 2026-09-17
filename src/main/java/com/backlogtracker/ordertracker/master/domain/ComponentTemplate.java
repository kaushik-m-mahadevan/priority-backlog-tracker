package com.backlogtracker.ordertracker.master.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.backlogtracker.commons.pattern.domain.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A reusable, group-scoped definition for a repeatable piece of a crocheted item — "Lily",
 *  "Vase base", "Sunflower" — with its own pattern and a typical crafting time. An order's
 *  actual component (see {@code Order.Component}) references one of these by id and
 *  snapshots its {@code baseCraftingTimeHours} at pick-time, same convention as
 *  {@link PresetOption} for packaging: editing a template later never retroactively
 *  changes a past order's estimate. Order-specific detail — which yarn/color was used
 *  this time, quantity, add-ons — lives on the order's component instance, not here. */
@Document("orderTrackerComponentTemplates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentTemplate {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private String label;
    private Pattern pattern;
    private double baseCraftingTimeHours;
    private String notes;
}
