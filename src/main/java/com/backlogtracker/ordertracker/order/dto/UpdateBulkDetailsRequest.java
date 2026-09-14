package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.PatternInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.ResearchItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.VariantInput;

/** Full replace of a bulk order's editable envelope — everything an individual order's
 *  {@link UpdateOrderRequest} covers except the individual-only materials/packaging/process
 *  fields (bulk keeps those per-variant), plus the variants themselves and coordinating
 *  creator. Reprices every variant and recomputes order-level totals/due date afterward.
 *  Variants carrying an existing {@code variantId} keep their split-progress history where
 *  the stageKey still matches; a variant with no id (or an id the order doesn't already
 *  have) is treated as new. */
public record UpdateBulkDetailsRequest(String customerId, String itemName, Instant orderReceivedDate,
                                       Instant quotedDeliveryDate, PatternInput pattern,
                                       List<ResearchItemInput> researchItems, double researchTimeHours,
                                       List<String> recipeSteps, String assemblyPackagingInstructions, String notes,
                                       List<VariantInput> variants, String coordinatingCreatorId,
                                       int logisticsBufferDays) {
}
