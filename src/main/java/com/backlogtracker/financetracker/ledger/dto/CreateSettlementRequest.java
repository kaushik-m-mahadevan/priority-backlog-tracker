package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;

import com.backlogtracker.financetracker.ledger.domain.SplitPartyType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code toPersonId} is never in the request body — it's always the caller, since only
 *  the person actually owed the money may record that they received it. */
public record CreateSettlementRequest(@NotNull SplitPartyType fromPartyType, String fromPersonId,
                                      @NotNull @Positive BigDecimal amount) {
}
