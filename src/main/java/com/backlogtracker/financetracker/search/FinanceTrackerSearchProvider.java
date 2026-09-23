package com.backlogtracker.financetracker.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.search.SearchResult;
import com.backlogtracker.commons.search.Searchable;
import com.backlogtracker.financetracker.ledger.domain.LedgerEntry;
import com.backlogtracker.financetracker.ledger.repository.LedgerEntryRepository;

import lombok.RequiredArgsConstructor;

/** ad-5: Finance Tracker's cross-applet search contribution — ledger entries (expenses and
 *  income) matched by description. */
@Component
@RequiredArgsConstructor
public class FinanceTrackerSearchProvider implements Searchable {

    private static final int MAX_RESULTS = 8;

    private final LedgerEntryRepository entries;
    private final GroupService groupService;

    @Override
    public String appletKey() {
        return Group.APPLET_FINANCE_TRACKER;
    }

    @Override
    public List<SearchResult> search(String groupId, String userId, String query) {
        groupService.requireMember(groupId, userId);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        for (LedgerEntry e : entries.findByGroupIdOrderByCreatedAtDesc(groupId)) {
            if (e.getDescription() != null && e.getDescription().toLowerCase(Locale.ROOT).contains(needle)) {
                String path = e.getType() == com.backlogtracker.financetracker.ledger.domain.LedgerEntryType.EXPENSE
                        ? "/financetracker/expenses" : "/financetracker/income";
                results.add(new SearchResult(
                        e.getType() == com.backlogtracker.financetracker.ledger.domain.LedgerEntryType.EXPENSE ? "Expense" : "Income",
                        e.getId(), e.getDescription(), e.getAmount() != null ? e.getAmount().toPlainString() : null, path));
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        return results;
    }
}
