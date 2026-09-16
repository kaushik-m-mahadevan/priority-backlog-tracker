package com.backlogtracker.productcatalog.colorway.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.backlogtracker.commons.pattern.domain.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One colorway in a business's product lineup, identified by name + colour together
 * (design decision, mirroring YarnType's own identity approach). Starts in the idea box
 * ({@code ideabox=true}) and is promoted to the catalog proper with a simple flag flip —
 * no other side effects. Business-wide: any member can create or edit one, same as
 * YarnType (design decision: any business member).
 */
@Document("productCatalogColorways")
@CompoundIndex(name = "group_identity", def = "{'groupId': 1, 'name': 1, 'colour': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Colorway {

    @Id
    private String id;

    private String groupId;

    private String name;
    private String colour;

    /** The design's pattern and recipe steps, shared with Order Tracker's own orders. */
    private Pattern pattern;

    private Double estimatedCost;
    private String notes;

    /** True while still an idea; flipped to false on promotion to the catalog proper. */
    @Builder.Default
    private boolean ideabox = true;

    private Instant createdAt;
}
