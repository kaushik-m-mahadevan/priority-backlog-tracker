package com.backlogtracker.financetracker.ledger.domain;

/** ad-1: who's on one side of a {@link LedgerEntry} — a real double-entry row is always
 *  exactly one Debit and one Credit, never a split. {@code CUSTOMER} and {@code EXTERNAL}
 *  are both a free display name (a customer's real name pulled from an order, or anyone
 *  else — a supplier, say); kept as separate types only so the UI can tell "this is a real
 *  order's customer" from "someone typed a name in" without a second lookup. Neither ever
 *  enters the internal owed-to-whom balance, which only nets {@code MEMBER} parties. */
public enum PartyType {
    MEMBER,
    BUSINESS,
    CUSTOMER,
    EXTERNAL
}
