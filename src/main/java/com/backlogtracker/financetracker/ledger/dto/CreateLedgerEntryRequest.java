package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.backlogtracker.financetracker.ledger.domain.PartyType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLedgerEntryRequest(
        Instant date,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull @Valid PartyInput debit,
        @NotNull @Valid PartyInput credit) {

    public record PartyInput(@NotNull PartyType type, String userId, String displayName) {
    }
}
