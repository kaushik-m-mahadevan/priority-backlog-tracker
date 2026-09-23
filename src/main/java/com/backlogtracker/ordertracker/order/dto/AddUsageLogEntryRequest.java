package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;

/** {@code yarnTypeId} points at a Material Inventory yarn type (opaque here, same
 *  convention as {@code Order.MandatoryItem.linkedYarnTypeId}). */
public record AddUsageLogEntryRequest(String yarnTypeId, double quantity, Instant date, String note) {
}
