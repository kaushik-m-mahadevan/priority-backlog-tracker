package com.backlogtracker.productcatalog.colorway.dto;

import java.util.List;

/** {@code recipeSteps} and {@code referenceLink} map directly onto {@code pattern};
 *  patternType/templateName/attachmentUrls have no UI yet and are left null/empty on
 *  create (design decision, deferred to a later pass). Photo attachments (ui-12) go
 *  through the generic image upload endpoints instead, keyed by the colorway's id, so
 *  they aren't part of this request. */
public record CreateColorwayRequest(String name, String colour, Double estimatedCost, String notes,
                                    List<String> recipeSteps, String referenceLink) {
}
