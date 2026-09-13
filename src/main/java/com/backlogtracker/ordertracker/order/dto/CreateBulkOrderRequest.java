package com.backlogtracker.ordertracker.order.dto;

import java.util.List;
import java.util.Map;

public record CreateBulkOrderRequest(String customerId, String description, List<VariantInput> variants,
                                     List<CreatorSplitInput> creatorSplits, String packagingPresetId,
                                     List<CreateOrderRequest.LineItemInput> itemizedPackaging, double materialsCost) {

    public record VariantInput(String label, int quantity, Map<String, String> mandatoryItems) {
    }

    public record CreatorSplitInput(String creatorId, int assignedQuantity, double estimatedHoursPerUnit) {
    }
}
