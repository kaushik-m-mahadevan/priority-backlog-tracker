package com.backlogtracker.ordertracker.order.domain;

/**
 * Derived from the payments ledger (design §6), never set directly.
 *
 * <p>{@link #FULLY_REFUNDED} exists specifically so a full refund is distinguishable from
 * never having been paid at all — both would otherwise read as {@code netPaid <= 0} under
 * the spec's literal formula (platform integration decision).
 */
public enum PaymentStatus {
    UNPAID, PARTIALLY_PAID, PAID, FULLY_REFUNDED
}
