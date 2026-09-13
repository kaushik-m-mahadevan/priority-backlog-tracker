package com.backlogtracker.ordertracker.master.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A configured origin-&gt;destination shipping leg for one group's business (design §2)
 *  — e.g. "Bangalore -&gt; Chennai" with its own default cost/time, so building a shipment
 *  plan (§7) is picking from configured lanes rather than re-estimating each time. */
@Document("orderTrackerShippingLanePresets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingLanePreset {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private String originLocationCode;
    private String destinationLocationCode;
    private double estimatedCost;
    private double estimatedTimeHours;
    private String note;
}
