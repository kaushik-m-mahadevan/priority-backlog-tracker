package com.backlogtracker.ordertracker.order.domain;

/**
 * Manually picked by the creator at order-entry time (round 5 delivery-estimate redesign) —
 * there's no structured, reliable customer address on an order yet to auto-detect this from,
 * so it's a judgment call, not a lookup. Picks which of {@code BusinessConfig}'s flat buffer
 * day-counts pads the delivery estimate. Real address-based tier detection is future work.
 */
public enum DeliveryTier {
    SAME_CITY,
    SAME_STATE,
    OTHER_STATE,
    INTERNATIONAL
}
