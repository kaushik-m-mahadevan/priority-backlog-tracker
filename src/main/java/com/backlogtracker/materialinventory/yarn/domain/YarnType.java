package com.backlogtracker.materialinventory.yarn.domain;

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

    /** Free-text extra detail (dye lot, texture) that doesn't affect identity. */
    private String notes;
}
