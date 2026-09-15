package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;

public record LedgerEntryView(String id, LedgerEntryType type, String description, BigDecimal amount,
                              String payerId, List<ShareView> shares, String createdByUserId, Instant createdAt) {

    public record ShareView(SplitPartyType partyType, String personId, BigDecimal ratio) {
        static ShareView of(LedgerEntry.SplitShare s) {
            return new ShareView(s.getPartyType(), s.getPersonId(), s.getRatio());
        }
    }

    public static LedgerEntryView of(LedgerEntry e) {
        return new LedgerEntryView(e.getId(), e.getType(), e.getDescription(), e.getAmount(), e.getPayerId(),
                e.getShares().stream().map(ShareView::of).toList(), e.getCreatedByUserId(), e.getCreatedAt());
    }
}
