package com.backlogtracker.financetracker.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
 * One money movement in a Finance Tracker group's shared ledger — an expense someone
 * paid, or income the business (or a specific person, e.g. an investor) received.
 * {@code BigDecimal} throughout (not {@code double}, unlike Order Tracker's cost math)
 * since a ledger accumulates many entries over time, where floating-point drift is a
 * real risk rather than a one-order rounding curiosity.
 *
 * <p>There is deliberately no separate "reimbursement" concept: a personal expense the
 * business owes back is just an EXPENSE whose only {@link SplitShare} is
 * {@code {BUSINESS, 1.0}} — the same shape a genuinely business-attributed cost (a
 * shipment paid on a personal card) already needs. {@code payerId} is always recorded,
 * even then, purely for an audit trail of who actually paid — it carries no ownership
 * implication by itself; the shares are what determine who owes what.
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

    private LedgerEntryType type;
    private String description;
    private BigDecimal amount;

    /** Who actually paid (EXPENSE) or received (INCOME) the money — always recorded,
     *  regardless of how the shares below attribute it. */
    private String payerId;

    /** Who bears each portion of {@link #amount}, summing to exactly 1 (validated by the
     *  service, not here — a domain object shouldn't throw on construction from
     *  already-persisted, already-valid data). Omitting a party from this list is how a
     *  personal expense's payer keeps their own absorbed portion out of what others owe
     *  them; it is not the same as a zero-ratio entry, which would be meaningless. */
    @Builder.Default
    private List<SplitShare> shares = new ArrayList<>();

    private String createdByUserId;

    @CreatedDate
    private Instant createdAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitShare {
        private SplitPartyType partyType;
        /** Set iff {@code partyType == PERSON}; null for {@code BUSINESS}. */
        private String personId;
        private BigDecimal ratio;
    }
}
