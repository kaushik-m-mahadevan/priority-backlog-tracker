package com.backlogtracker.ordertracker.order.dto;

/** Split-tracked bulk stage (e.g. Crocheting, Assembly) — progress for one creator's share
 *  of one variant (spec §5.11). */
public record UpdateBulkSplitProgressRequest(String variantId, String creatorId, String stageKey, int unitsCompleted) {
}
