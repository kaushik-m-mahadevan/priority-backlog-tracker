package com.backlogtracker.materialinventory.inventory.service;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.inventory.InventoryUsageConsumer;

import lombok.RequiredArgsConstructor;

/** ad-2's cross-applet {@link InventoryUsageConsumer} — a thin wrapper around the same
 *  atomic {@link InventoryService#adjustQuantity} already used for yarn transfers. */
@Component
@RequiredArgsConstructor
public class MaterialInventoryUsageConsumer implements InventoryUsageConsumer {

    private final InventoryService inventoryService;

    @Override
    public String appletKey() {
        return Group.APPLET_MATERIAL_INVENTORY;
    }

    @Override
    public void adjustQuantity(String groupId, String userId, String yarnTypeId, double delta) {
        inventoryService.adjustQuantity(groupId, userId, yarnTypeId, delta);
    }
}
