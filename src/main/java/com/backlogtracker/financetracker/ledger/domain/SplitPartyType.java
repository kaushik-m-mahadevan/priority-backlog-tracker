package com.backlogtracker.financetracker.ledger.domain;

/** Who bears a {@link LedgerEntry.SplitShare} of an entry: a specific person (they owe
 *  or are owed their portion), or the business collectively — the mechanism a fully
 *  business-attributed expense and a reimbursement both use (design: a reimbursement is
 *  just an expense where the payer's own share is 0% and BUSINESS is 100%, not a separate
 *  concept). */
public enum SplitPartyType {
    PERSON,
    BUSINESS
}
