package com.backlogtracker.materialinventory.inventory.domain;

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
 * One person's on-hand quantity of one {@code YarnType}, in this business. One row per
 * (groupId, userId, yarnTypeId) — quantity is set directly (an upsert), not accumulated
 * from movements, since this is a snapshot of what's on hand rather than a ledger.
 * {@code quantity} is in skeins and must be a multiple of 0.25 (design decision).
 *
 * <p>Visible business-wide (design decision, same "full transparency" precedent as
 * Finance Tracker's balances) — any member can see everyone's inventory, not just their
 * own, but only the owning {@code userId} may change their own row.
 */
@Document("materialInventoryEntries")
@CompoundIndex(name = "group_user_yarnType", def = "{'groupId': 1, 'userId': 1, 'yarnTypeId': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEntry {

    @Id
    private String id;

    private String groupId;
    private String userId;
    private String yarnTypeId;

    private double quantity;

    private Instant updatedAt;
}
