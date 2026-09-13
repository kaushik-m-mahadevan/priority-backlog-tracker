package com.backlogtracker.ordertracker.order.dto;

import java.util.List;
import java.util.Map;

public record CreateOrderRequest(String customerId, String primaryCreatorId, String description,
                                 Map<String, String> mandatoryItems, List<String> addOns,
                                 String packagingPresetId, List<LineItemInput> itemizedPackaging,
                                 List<StageInput> stages, double materialsCost) {

    public record StageInput(String stageKey, String assigneeCreatorId, double estimatedHours) {
    }

    public record LineItemInput(String label, double cost, double timeHours) {
    }
}
