package com.backlogtracker.financetracker.ledger.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.ExternalPartySuggestion;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.dto.MemberBalanceView;
import com.backlogtracker.financetracker.ledger.service.LedgerEntryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financetracker/groups/{groupId}/ledger")
@RequiresUser
@RequiredArgsConstructor
public class LedgerEntryController {

    private final LedgerEntryService ledgerEntryService;

    @GetMapping
    public List<LedgerEntryView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.list(groupId, actor.id());
    }

    @GetMapping("/balances")
    public List<MemberBalanceView> balances(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.balances(groupId, actor.id());
    }

    @GetMapping("/external-suggestions")
    public List<ExternalPartySuggestion> externalSuggestions(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.externalSuggestions(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryView create(@PathVariable String groupId, @Valid @RequestBody CreateLedgerEntryRequest request,
                                  @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.create(groupId, actor.id(), request);
    }

    @PutMapping("/{entryId}")
    public LedgerEntryView update(@PathVariable String groupId, @PathVariable String entryId,
                                  @Valid @RequestBody CreateLedgerEntryRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.update(groupId, actor.id(), entryId, request);
    }

    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String groupId, @PathVariable String entryId, @AuthenticationPrincipal AuthUser actor) {
        ledgerEntryService.delete(groupId, actor.id(), entryId);
    }
}
