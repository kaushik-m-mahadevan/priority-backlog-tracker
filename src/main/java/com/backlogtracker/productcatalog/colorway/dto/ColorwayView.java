package com.backlogtracker.productcatalog.colorway.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.commons.pattern.domain.Pattern;
import com.backlogtracker.commons.pattern.domain.PatternType;
import com.backlogtracker.productcatalog.colorway.domain.Colorway;

public record ColorwayView(String id, String name, String colour, PatternView pattern, Double estimatedCost,
                           String notes, boolean ideabox, Instant createdAt) {

    /** The create/edit form surfaces {@code recipeSteps} and {@code referenceLink} (ui-12)
     *  — the other Pattern fields (patternType, templateName, attachmentUrls) round-trip
     *  here but have no UI yet (design decision: defer to a later pass). */
    public record PatternView(PatternType patternType, String templateName, String customPatternNotes,
                              List<String> attachmentUrls, List<String> recipeSteps, String referenceLink) {
        static PatternView of(Pattern p) {
            return p == null ? null : new PatternView(p.getPatternType(), p.getTemplateName(),
                    p.getCustomPatternNotes(), p.getAttachmentUrls(), p.getRecipeSteps(), p.getReferenceLink());
        }
    }

    public static ColorwayView of(Colorway c) {
        return new ColorwayView(c.getId(), c.getName(), c.getColour(), PatternView.of(c.getPattern()),
                c.getEstimatedCost(), c.getNotes(), c.isIdeabox(), c.getCreatedAt());
    }
}
