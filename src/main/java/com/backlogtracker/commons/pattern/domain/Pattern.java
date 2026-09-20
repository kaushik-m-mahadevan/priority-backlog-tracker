package com.backlogtracker.commons.pattern.domain;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A design's pattern and how-to steps together — moved into commons (design decision:
 * refactor into a shared piece) so both Order Tracker's orders and Product Catalog's
 * Colorways use the same embedded type instead of each defining their own. Previously
 * Order Tracker kept {@code recipeSteps} as a separate top-level field alongside
 * {@code pattern}; they're merged here since the recipe genuinely is part of the pattern
 * — a design's "how to make it" — not an independent concept.
 *
 * <p>{@code patternType} may be null even when {@code recipeSteps} is populated (someone
 * can jot down steps without picking a formal template/custom classification); a wholly
 * absent {@code Pattern} (the embedding field left null) means nothing about the design
 * has been recorded at all.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pattern {

    private PatternType patternType;
    private String templateName;
    private String customPatternNotes;
    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();
    @Builder.Default
    private List<String> recipeSteps = new ArrayList<>();
    /** External link to the pattern's source (e.g. a Ravelry/Etsy page) — independent of
     *  any uploaded photos (see {@link com.backlogtracker.commons.image.domain.ImageAsset}):
     *  a design can have a photo, a link, both, or neither (ui-12: not either/or). */
    private String referenceLink;
}
