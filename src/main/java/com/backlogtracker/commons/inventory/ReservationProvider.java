package com.backlogtracker.commons.inventory;

import java.util.Map;

/**
 * Implemented by one Spring bean (Order Tracker) so Material Inventory can show "N
 * reserved" on a person's on-hand yarn (ad-2), without importing across applets —
 * same bean-discovery shape as {@link com.backlogtracker.commons.search.Searchable}.
 */
public interface ReservationProvider {

    /** One of the {@code Group.APPLET_*} constants — which applet's groups this provider
     *  can compute reservations for. */
    String appletKey();

    /** yarnTypeId -&gt; total quantity currently reserved against {@code userId}'s own
     *  stash by their open (non-cancelled, non-delivered) work in {@code groupId}. Never
     *  negative; a yarnTypeId with nothing reserved is simply absent from the map. */
    Map<String, Double> reservedQuantities(String groupId, String userId);
}
