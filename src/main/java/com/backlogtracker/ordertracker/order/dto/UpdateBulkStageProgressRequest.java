package com.backlogtracker.ordertracker.order.dto;

/** Batch-tracked bulk stage (e.g. Packaging, Shipment) — one shared count across the whole
 *  order, not per creator/variant (spec §5.11). */
public record UpdateBulkStageProgressRequest(int unitsCompleted) {
}
