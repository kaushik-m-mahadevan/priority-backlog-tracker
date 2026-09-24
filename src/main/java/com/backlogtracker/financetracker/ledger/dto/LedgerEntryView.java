package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;
import com.backlogtracker.financetracker.ledger.domain.PartyType;

public record LedgerEntryView(String id, Instant date, String description, BigDecimal amount,
                              PartyView debit, PartyView credit, String sourceRef, String orderReference,
                              String createdByUserId, Instant createdAt) {

    public record PartyView(PartyType type, String userId, String displayName) {
        static PartyView of(LedgerEntry.Party p) {
            return new PartyView(p.getType(), p.getUserId(), p.getDisplayName());
        }
    }

    public static LedgerEntryView of(LedgerEntry e) {
        return new LedgerEntryView(e.getId(), e.getDate(), e.getDescription(), e.getAmount(),
                PartyView.of(e.getDebit()), PartyView.of(e.getCredit()), e.getSourceRef(), e.getOrderReference(),
                e.getCreatedByUserId(), e.getCreatedAt());
    }
}
