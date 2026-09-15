package com.backlogtracker.materialinventory.inventory.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.inventory.domain.InventoryEntry;

/** {@code stale} approximates "hasn't this person's on-hand yarn count probably drifted
 *  from reality" using the entry's own {@code updatedAt} — a real cross-applet activity
 *  signal (has this person touched an order in Order Tracker recently, etc.) is out of
 *  scope for a first version since it would need new commons plumbing across applets that
 *  don't otherwise depend on each other; this is a deliberately simple placeholder, honest
 *  about being an approximation, that can be swapped for a real activity signal later
 *  without changing this view's shape. */
public record InventoryEntryView(String id, String userId, String yarnTypeId, double quantity, Instant updatedAt,
                                 boolean stale) {

    public static InventoryEntryView of(InventoryEntry e, boolean stale) {
        return new InventoryEntryView(e.getId(), e.getUserId(), e.getYarnTypeId(), e.getQuantity(), e.getUpdatedAt(), stale);
    }
}
