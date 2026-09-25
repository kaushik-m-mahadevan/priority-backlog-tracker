package com.backlogtracker.financetracker.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ad-1: one real cash movement — always exactly one Debit party and one Credit party,
 * never a multi-way split. Replaces the earlier shares/ratios model entirely: a shared
 * expense is no longer one ledger construct, it's however many real transactions actually
 * happened (each entered separately) — see BACKLOG.md's ad-1 design note. {@code
 * BigDecimal} throughout (not {@code double}) since a ledger accumulates many entries over
 * time, where floating-point drift is a real risk.
 */
@Document("financeLedgerEntries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private Instant date;
    private String description;
    private BigDecimal amount;

    private Party debit;
    private Party credit;

    /** Null for a manually-entered row. Set to {@code "<orderId>:<paymentId>"} for a row
     *  auto-created from an Order Tracker payment — the idempotency key a backfill or a
     *  retried sync can safely check against before inserting again, and what a payment
     *  removal looks up to delete the matching row. */
    @Indexed
    private String sourceRef;

    /** mb-16: an optional free-text order reference a member types in when manually
     *  entering a ledger row for a specific order (e.g. "reimbursed for yarn on Order
     *  #94561842000001") — same free-text-reference convention as
     *  {@code ProfitDistributionService}'s own {@code orderReferences}, deliberately not a
     *  live cross-applet read. Independent of {@link #sourceRef}: this is a member's own
     *  note, not the auto-sync idempotency key, so a manual and a synced row can both
     *  reference the same real order without colliding. */
    private String orderReference;

    /** Free-text notes a member adds after the fact — how it was paid, a tracking detail,
     *  anything not worth its own field. Editable even on an auto-synced row (everything
     *  else on a synced row stays locked to its Order Tracker source). */
    private String notes;

    private String createdByUserId;

    @CreatedDate
    private Instant createdAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Party {
        private PartyType type;
        /** Set iff {@code type == MEMBER}. */
        private String userId;
        /** Set iff {@code type == CUSTOMER} or {@code EXTERNAL} — the real customer's name
         *  (pulled from the order, never a generic label) or a free-text external name. */
        private String displayName;
    }
}
