package com.backlogtracker.materialinventory.inventory.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.inventory.domain.InventoryEntry;

/** {@code stale} approximates "hasn't this person's on-hand yarn count probably drifted
 *  from reality" using the entry's own {@code updatedAt} — a real cross-applet activity
 *  signal (has this person touched an order in Order Tracker recently, etc.) is out of
 *  scope for a first version since it would need new commons plumbing across applets that
 *  don't otherwise depend on each other; this is a deliberately simple placeholder, honest
 *  about being an approximation, that can be swapped for a real activity signal later
 *  without changing this view's shape.
 *
 *  <p>ad-2: {@code reserved}/{@code available} come from the linked Order Tracker
 *  business's open orders (0/{@code quantity} when nothing is linked or nothing is
 *  reserved). {@code personalLow} flags this owner's own stash at ≤1 skein;
 *  {@code businessLow} flags the yarn type's business-wide total (across every member) at
 *  ≤1 skein — both computed fresh on read, not stored. */
public record InventoryEntryView(String id, String userId, String yarnTypeId, double quantity, Instant updatedAt,
                                 boolean stale, double reserved, double available,
                                 boolean personalLow, boolean businessLow) {

    public static InventoryEntryView of(InventoryEntry e, boolean stale, double reserved,
                                        boolean personalLow, boolean businessLow) {
        double available = Math.max(0, e.getQuantity() - reserved);
        return new InventoryEntryView(e.getId(), e.getUserId(), e.getYarnTypeId(), e.getQuantity(), e.getUpdatedAt(),
                stale, reserved, available, personalLow, businessLow);
    }
}
