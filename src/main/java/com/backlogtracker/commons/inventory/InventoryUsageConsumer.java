package com.backlogtracker.commons.inventory;

/**
 * Implemented by one Spring bean (Material Inventory) so Order Tracker's usage-log/sync
 * flow (ad-2) can decrement a person's real on-hand yarn without importing across applets
 * (commons.* is the only package any applet may import from) — same bean-discovery shape
 * as {@link com.backlogtracker.commons.search.Searchable}.
 */
public interface InventoryUsageConsumer {

    /** One of the {@code Group.APPLET_*} constants — which applet's groups this consumer
     *  can adjust inventory for. */
    String appletKey();

    /** {@code delta} negative withdraws (rejected if it would go below zero), positive
     *  deposits (restoring a removed usage-log entry). Caller is trusted application code
     *  that has already authorized the action on its own side. */
    void adjustQuantity(String groupId, String userId, String yarnTypeId, double delta);
}
