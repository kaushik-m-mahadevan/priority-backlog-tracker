package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

/** Set whichever of the two is now known; the other may be null still. */
public record MarkLegRequest(Instant shippedAt, Instant deliveredAt) {
}
