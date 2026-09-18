package com.backlogtracker.financetracker.profitsplit.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code totalProfit} is entered manually by the proposer (typically read off Order
 * Tracker's finalized order profit) rather than fetched live — Finance Tracker and Order
 * Tracker are separate applets linked only by {@code GroupLink}, and this keeps the split
 * self-contained without adding a live cross-applet data read.
 *
 * <p>{@code orderReferences} is a free-text list rather than a single value, since one
 * split can legitimately cover several orders batched together (design decision, round 5
 * review) — e.g. settling profit across the last ten orders in one go instead of one
 * proposal per order. An empty list means a "general settlement" not tied to any specific
 * order at all (e.g. the proposer genuinely doesn't remember which order this covers) —
 * this is a valid, ordinary case, not an error.
 *
 * <p>Each recipient's split defaults to proportional-by-{@code unitsCompleted} (design
 * decision), unless {@code overrideAmount} is set, which takes that recipient's share out
 * of the proportional pool entirely and fixes it at the given amount — the coordinator's
 * manual override "for any reason creators deem fit".
 */
public record ProposeProfitDistributionRequest(List<String> orderReferences, BigDecimal totalProfit,
                                               List<RecipientInput> recipients) {

    public record RecipientInput(String personId, int unitsCompleted, BigDecimal overrideAmount) {
    }
}
