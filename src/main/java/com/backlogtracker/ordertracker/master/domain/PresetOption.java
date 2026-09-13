package com.backlogtracker.ordertracker.master.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Packaging preset for one group's business (design §2) — e.g. "Box", "Padded envelope"
 *  with a default cost/time, so the packaging estimate is picking from configured options
 *  rather than re-estimating each time. */
@Document("orderTrackerPresetOptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetOption {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private String label;
    private double estimatedCost;
    private double estimatedTimeHours;
}
