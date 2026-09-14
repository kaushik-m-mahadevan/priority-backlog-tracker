package com.backlogtracker.ordertracker.order.dto;

import java.util.List;

import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.VariantInput;

/** Full replace of a bulk order's variants + coordinating creator — reprices every variant
 *  and recomputes order-level totals/due date afterward. Variants carrying an existing
 *  {@code variantId} keep their split-progress history where the stageKey still matches;
 *  a variant with no id (or an id the order doesn't already have) is treated as new. */
public record UpdateBulkDetailsRequest(List<VariantInput> variants, String coordinatingCreatorId,
                                       int logisticsBufferDays) {
}
