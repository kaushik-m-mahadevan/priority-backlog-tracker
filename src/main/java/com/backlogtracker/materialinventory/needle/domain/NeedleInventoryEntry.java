package com.backlogtracker.materialinventory.needle.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person's on-hand count of one {@code NeedleType} — whole units (you either have a
 * hook or you don't, no fractional steps like yarn's quarter-skein rule). Same visibility
 * rule as yarn's {@code InventoryEntry}: business-wide read, self-only write.
 */
@Document("materialInventoryNeedleEntries")
@CompoundIndex(name = "group_user_needleType", def = "{'groupId': 1, 'userId': 1, 'needleTypeId': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeedleInventoryEntry {

    @Id
    private String id;

    private String groupId;
    private String userId;
    private String needleTypeId;

    private int quantity;

    private Instant updatedAt;
}
