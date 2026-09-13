package com.backlogtracker.backlogtracker.ranking.dto;

import java.util.List;

/**
 * The Pecking Order response. Pinned items lead (in score order), then the rest fill the
 * remaining slots. {@code overPinned} is true when more items are pinned than fit in the
 * list — the UI then shows only the top {@code limit} pins plus a "too many pins" warning.
 */
public record TopListView(
        List<RankedItemView> items,
        int pinnedCount,
        boolean overPinned) {
}
