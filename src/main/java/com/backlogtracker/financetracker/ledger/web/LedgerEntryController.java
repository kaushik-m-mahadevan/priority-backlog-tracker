package com.backlogtracker.financetracker.ledger.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.financetracker.ledger.dto.BalanceView;
import com.backlogtracker.financetracker.ledger.dto.CreateLedgerEntryRequest;
import com.backlogtracker.financetracker.ledger.dto.CreateSettlementRequest;
import com.backlogtracker.financetracker.ledger.dto.LedgerEntryView;
import com.backlogtracker.financetracker.ledger.dto.SettlementView;
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
    public List<BalanceView> balances(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.balances(groupId, actor.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryView create(@PathVariable String groupId, @Valid @RequestBody CreateLedgerEntryRequest request,
                                  @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.create(groupId, actor.id(), request);
    }

    @GetMapping("/settlements")
    public List<SettlementView> settlements(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.listSettlements(groupId, actor.id());
    }

    /** Only the caller (the person actually owed the money) may create this — see
     *  {@link com.backlogtracker.financetracker.ledger.domain.Settlement}. */
    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementView settleUp(@PathVariable String groupId, @Valid @RequestBody CreateSettlementRequest request,
                                   @AuthenticationPrincipal AuthUser actor) {
        return ledgerEntryService.settleUp(groupId, actor.id(), request);
    }
}
