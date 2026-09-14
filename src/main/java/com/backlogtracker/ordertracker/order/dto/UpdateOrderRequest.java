package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.LineItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.MandatoryItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.PatternInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.ResearchItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.ToolInput;

/** Full replace of an individual order's editable envelope (every card on the order
 *  screen except status/payments/shipment/stage-assignment, which have their own focused
 *  endpoints) — recomputes {@code costEstimate} afterward. */
public record UpdateOrderRequest(String customerId, String itemName, Instant orderReceivedDate,
                                 Instant quotedDeliveryDate,
                                 PatternInput pattern, List<ResearchItemInput> researchItems,
                                 double researchTimeHours, List<String> recipeSteps, String assemblyPackagingInstructions,
                                 List<MandatoryItemInput> mandatoryItems, List<ToolInput> tools,
                                 List<LineItemInput> addOns, String packagingPresetId,
                                 List<LineItemInput> itemizedPackaging, double craftingTimeHours,
                                 double assemblyTimeHours) {
}
