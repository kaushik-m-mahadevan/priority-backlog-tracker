package com.backlogtracker.ordertracker.order.domain;

/**
 * Derived from the payments ledger (spec §6), never set directly.
 * {@code netPaid = Σ(amount where type != REFUND) - Σ(amount where type == REFUND)}.
 *
 * <p>{@link #REFUNDED} additionally covers the {@code netPaid == 0} case when at least one
 * REFUND entry exists (a full refund exactly cancelling out prior payments) — the spec's
 * literal {@code netPaid < 0} threshold would otherwise read that identically to
 * {@link #UNPAID}, which platform-integration decision explicitly calls out as needing a
 * concrete resolution (distinguish "never paid" from "paid in full then fully refunded").
 */
public enum PaymentStatus {
    UNPAID, PARTIALLY_PAID, PAID_IN_FULL, REFUNDED
}
