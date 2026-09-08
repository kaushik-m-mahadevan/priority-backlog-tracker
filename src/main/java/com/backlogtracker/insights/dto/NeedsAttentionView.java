package com.backlogtracker.insights.dto;

import java.util.List;

import com.backlogtracker.item.dto.ItemView;

/**
 * The "Needs Attention" panel (design §5). A pending archival request has no effect on
 * either list — items are evaluated on their real fields only.
 */
public record NeedsAttentionView(
        List<FlaggedItem> staleAndOverdue,
        List<FlaggedItem> buriedLowPriority) {

    /** An item plus the metric that flagged it ({@code days} overdue, or {@code days} old). */
    public record FlaggedItem(ItemView item, long days) {
    }
}
