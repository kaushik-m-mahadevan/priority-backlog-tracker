package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;

/** ad-1: one member's live-computed net standing — sum of every ledger row crediting them
 *  minus every row debiting them (MEMBER-party rows only; BUSINESS/CUSTOMER/EXTERNAL rows
 *  never enter this). Positive means the group's ledger currently shows this member as net
 *  owed; negative means they owe. Never stored — recomputed from the ledger on every read. */
public record MemberBalanceView(String userId, BigDecimal net) {
}
