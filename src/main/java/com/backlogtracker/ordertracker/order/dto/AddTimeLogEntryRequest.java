package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

import com.backlogtracker.ordertracker.order.domain.Order.TimeStage;

/** {@code variantId} is required for CRAFTING/ASSEMBLY on a bulk order (matching how those
 *  estimates are already per-variant) and must be null everywhere else — RESEARCH is
 *  always whole-order, and an INDIVIDUAL order has no variants at all. {@code componentId}
 *  is only meaningful for CRAFTING when the order (or, for bulk, the named variant) has
 *  been broken into components — it must be null for RESEARCH/ASSEMBLY, which stay
 *  order/variant-level always, never per-component. */
public record AddTimeLogEntryRequest(TimeStage stage, double hours, Instant date, String note, String variantId,
                                     String componentId) {
}
