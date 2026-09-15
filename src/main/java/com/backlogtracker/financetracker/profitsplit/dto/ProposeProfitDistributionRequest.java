package com.backlogtracker.financetracker.profitsplit.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code totalProfit} is entered manually by the proposer (typically read off Order
 * Tracker's finalized order profit) rather than fetched live — Finance Tracker and Order
 * Tracker are separate applets linked only by {@code GroupLink}, and this keeps the split
 * self-contained without adding a live cross-applet data read.
 *
 * <p>Each recipient's split defaults to proportional-by-{@code unitsCompleted} (design
 * decision), unless {@code overrideAmount} is set, which takes that recipient's share out
 * of the proportional pool entirely and fixes it at the given amount — the coordinator's
 * manual override "for any reason creators deem fit".
 */
public record ProposeProfitDistributionRequest(String orderReference, BigDecimal totalProfit,
                                               List<RecipientInput> recipients) {

    public record RecipientInput(String personId, int unitsCompleted, BigDecimal overrideAmount) {
    }
}
