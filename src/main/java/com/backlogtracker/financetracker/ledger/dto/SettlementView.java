package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.backlogtracker.financetracker.ledger.domain.Settlement;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;

public record SettlementView(String id, SplitPartyType fromPartyType, String fromPersonId, String toPersonId,
                             BigDecimal amount, Instant createdAt) {

    public static SettlementView of(Settlement s) {
        return new SettlementView(s.getId(), s.getFromPartyType(), s.getFromPersonId(), s.getToPersonId(),
                s.getAmount(), s.getCreatedAt());
    }
}
