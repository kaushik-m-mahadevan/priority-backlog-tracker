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
 * Records that money actually changed hands to settle part of a ledger balance — kept as
 * its own concept rather than another {@link LedgerEntry}, since a settlement is a
 * pairwise transfer between two named parties (who paid whom), not a payer-plus-shares
 * split. Applied on top of the ledger-derived balance the same way a {@code PERSON} or
 * {@code BUSINESS} share already is: a settlement from A to B reduces A's debt and
 * reduces what's owed to B by the same amount, symmetric to how a share works in reverse.
 *
 * <p>Only {@code toPersonId} — the person actually owed the money — may create a
 * settlement (enforced by the service, not here): they're the one confirming they
 * received it, per the design decision that only the person owed can mark something
 * settled.
 */
@Document("financeSettlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    private String id;

    @Indexed
    private String groupId;

    /** Who paid: a specific person, or the business itself (e.g. the business reimbursed
     *  someone's fronted expense). Same shape as {@link LedgerEntry.SplitShare}. */
    private SplitPartyType fromPartyType;
    /** Set iff {@code fromPartyType == PERSON}; null for {@code BUSINESS}. */
    private String fromPersonId;

    /** Who received the money — always a specific person, and always the one who
     *  created this record. */
    private String toPersonId;

    private BigDecimal amount;

    private String createdByUserId;

    @CreatedDate
    private Instant createdAt;
}
