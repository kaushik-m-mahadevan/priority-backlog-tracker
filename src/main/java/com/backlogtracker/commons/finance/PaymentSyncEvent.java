package com.backlogtracker.commons.finance;

import java.time.Instant;

/**
 * ad-1: one Order Tracker payment (or refund), described generically enough for Finance
 * Tracker to turn into a Debit/Credit ledger row without either applet importing the
 * other's domain types — {@code sourceRef} is the idempotency key a backfill or a repeat
 * delivery can safely retry against.
 */
public record PaymentSyncEvent(String sourceRef, Instant date, String description, double amount,
                               PartyRef debit, PartyRef credit, String orderReference) {

    /** {@code kind} is one of "MEMBER" ({@code userId} set), "BUSINESS" (neither set), or
     *  "CUSTOMER"/"EXTERNAL" ({@code displayName} set). */
    public record PartyRef(String kind, String userId, String displayName) {
        public static PartyRef member(String userId) {
            return new PartyRef("MEMBER", userId, null);
        }

        public static PartyRef business() {
            return new PartyRef("BUSINESS", null, null);
        }

        public static PartyRef customer(String name) {
            return new PartyRef("CUSTOMER", null, name);
        }
    }
}
