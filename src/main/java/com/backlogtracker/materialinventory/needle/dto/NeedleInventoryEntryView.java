package com.backlogtracker.materialinventory.needle.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.needle.domain.NeedleInventoryEntry;

public record NeedleInventoryEntryView(String id, String userId, String needleTypeId, int quantity, Instant updatedAt) {

    public static NeedleInventoryEntryView of(NeedleInventoryEntry e) {
        return new NeedleInventoryEntryView(e.getId(), e.getUserId(), e.getNeedleTypeId(), e.getQuantity(), e.getUpdatedAt());
    }
}
