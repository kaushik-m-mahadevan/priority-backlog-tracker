package com.backlogtracker.materialinventory.needle.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A canonical crochet hook or knitting needle size for one business — kept as its own
 * catalog, separate from {@code YarnType}, since hooks/needles are reusable tools rather
 * than a consumable material (design decision: a separate section for needles). Identity
 * is {@code kind + size} together (e.g. CROCHET_HOOK "4mm / US H-8"), same shape as
 * YarnType's own identity rule.
 */
@Document("materialInventoryNeedleTypes")
@CompoundIndex(name = "group_identity", def = "{'groupId': 1, 'kind': 1, 'size': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeedleType {

    @Id
    private String id;

    private String groupId;

    private NeedleKind kind;
    private String size;

    private String notes;
}
