package com.backlogtracker.financetracker.ledger.dto;

import java.math.BigDecimal;

/**
 * One person's computed standing in the group's ledger — two deliberately separate
 * numbers, not netted into one (per design decision: "business owes you" and "what other
 * people owe you" are different relationships and shouldn't be conflated):
 *
 * <p>{@code netFromOthers} — positive means other members collectively owe this person
 * that much (they paid an expense someone else's share covered); negative means this
 * person owes other members. Comes only from EXPENSE entries' PERSON shares, excluding a
 * payer's own share of their own expense (that's an absorbed personal cost, not a debt).
 *
 * <p>{@code owedByBusiness} — always &gt;= 0. What the business owes this person: the
 * BUSINESS-share portion of expenses they paid (a business-attributed expense, including
 * a reimbursement) plus any INCOME credited to them (e.g. an investment) — both are "you
 * put money into the business's hands, it owes you back" in exactly the same shape.
 */
public record BalanceView(String personId, BigDecimal netFromOthers, BigDecimal owedByBusiness) {
}
