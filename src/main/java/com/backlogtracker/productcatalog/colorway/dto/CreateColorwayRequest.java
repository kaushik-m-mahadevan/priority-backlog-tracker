package com.backlogtracker.productcatalog.colorway.dto;

import java.util.List;

/** v1 form scope (design decision, deferred to a later pass): {@code recipeSteps} maps
 *  directly onto {@code pattern.recipeSteps}; patternType/templateName/attachmentUrls
 *  have no UI yet and are left null/empty on create. */
public record CreateColorwayRequest(String name, String colour, Double estimatedCost, String notes,
                                    List<String> recipeSteps) {
}
