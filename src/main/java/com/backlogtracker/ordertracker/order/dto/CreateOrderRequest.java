package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.backlogtracker.commons.pattern.domain.PatternType;
import com.backlogtracker.ordertracker.order.domain.Order.ResearchItemType;

public record CreateOrderRequest(String customerId, String orderType, String createdByCreatorId,
                                 String itemName, Instant orderReceivedDate, Instant quotedDeliveryDate,
                                 PatternInput pattern, List<ResearchItemInput> researchItems,
                                 double researchTimeHours, String assemblyPackagingInstructions, String notes,
                                 // individual-only
                                 List<MandatoryItemInput> mandatoryItems, List<ToolInput> tools,
                                 List<LineItemInput> addOns,
                                 String packagingPresetId, List<LineItemInput> itemizedPackaging,
                                 double craftingTimeHours, double assemblyTimeHours,
                                 // bulk-only
                                 List<VariantInput> variants, String coordinatingCreatorId,
                                 int logisticsBufferDays) {

    /** {@code recipeSteps} lives here now, not as a separate top-level order field — see
     *  {@link com.backlogtracker.commons.pattern.domain.Pattern} for why they're merged. */
    public record PatternInput(PatternType patternType, String templateName, String customPatternNotes,
                               List<String> attachmentUrls, List<String> recipeSteps) {
    }

    public record ResearchItemInput(ResearchItemType type, String url, String description) {
    }

    public record MandatoryItemInput(String itemKey, String value, double quantity, double unitCost, String notes,
                                     String linkedYarnTypeId) {
    }

    public record ToolInput(String itemKey, String value, String notes) {
    }

    public record LineItemInput(String name, String category, Map<String, String> attributes,
                                double quantity, double unitCost, Double unitTimeHours, String note) {
    }

    public record SplitLineInput(String creatorId, int quantityAssigned) {
    }

    public record VariantInput(String variantId, String label, int quantity,
                               List<MandatoryItemInput> mandatoryItems, List<ToolInput> tools,
                               List<LineItemInput> addOns,
                               String packagingPresetId, List<LineItemInput> itemizedPackaging,
                               double craftingTimeHours, double assemblyTimeHours,
                               List<SplitLineInput> splitAllocation) {
    }
}
