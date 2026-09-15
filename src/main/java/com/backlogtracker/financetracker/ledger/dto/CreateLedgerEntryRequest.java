package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

import com.backlogtracker.financetracker.ledger.domain.LedgerEntryType;
import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLedgerEntryRequest(
        @NotNull LedgerEntryType type,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String payerId,
        @NotEmpty List<@Valid ShareInput> shares) {

    public record ShareInput(@NotNull SplitPartyType partyType, String personId, @NotNull BigDecimal ratio) {
    }
}
