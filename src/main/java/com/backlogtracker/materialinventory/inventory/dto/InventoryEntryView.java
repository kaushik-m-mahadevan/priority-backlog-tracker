package com.backlogtracker.materialinventory.inventory.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.inventory.domain.InventoryEntry;

public record InventoryEntryView(String id, String userId, String yarnTypeId, double quantity, Instant updatedAt) {

    public static InventoryEntryView of(InventoryEntry e) {
        return new InventoryEntryView(e.getId(), e.getUserId(), e.getYarnTypeId(), e.getQuantity(), e.getUpdatedAt());
    }
}
