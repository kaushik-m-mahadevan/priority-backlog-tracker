package com.backlogtracker.financetracker.ledger.dto;

/** ad-1: a previously-typed external party name, for the "who else" suggestions dropdown
 *  on a manual ledger entry — sorted by how often each has been used, most-used first. */
public record ExternalPartySuggestion(String displayName, long useCount) {
}
