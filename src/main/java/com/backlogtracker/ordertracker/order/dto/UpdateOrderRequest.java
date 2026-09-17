package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.ComponentInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.LineItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.MandatoryItemInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.PatternInput;
import com.backlogtracker.ordertracker.order.dto.CreateOrderRequest.ResearchItemInput;

/** Full replace of an individual order's editable envelope (every card on the order
 *  screen except status/payments/shipment/stage-assignment, which have their own focused
 *  endpoints) — recomputes {@code costEstimate} afterward. */
public record UpdateOrderRequest(String customerId, String itemName, Instant orderReceivedDate,
                                 Instant quotedDeliveryDate,
                                 PatternInput pattern, List<ResearchItemInput> researchItems,
                                 double researchTimeHours, String assemblyPackagingInstructions, String notes,
                                 List<MandatoryItemInput> mandatoryItems,
                                 List<LineItemInput> addOns, List<ComponentInput> components,
                                 String packagingPresetId,
                                 List<LineItemInput> itemizedPackaging, double craftingTimeHours,
                                 double assemblyTimeHours) {
}
