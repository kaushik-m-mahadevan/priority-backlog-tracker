package com.backlogtracker.financetracker.profitsplit.web;

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

import com.backlogtracker.commons.finance.OrderSplitLookup;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.financetracker.profitsplit.dto.ProfitDistributionView;
import com.backlogtracker.financetracker.profitsplit.dto.ProposeProfitDistributionRequest;
import com.backlogtracker.financetracker.profitsplit.service.ProfitDistributionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financetracker/groups/{groupId}/profit-distributions")
@RequiresUser
@RequiredArgsConstructor
public class ProfitDistributionController {

    private final ProfitDistributionService profitDistributionService;

    @GetMapping
    public List<ProfitDistributionView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.list(groupId, actor.id());
    }

    @GetMapping("/{requestId}")
    public ProfitDistributionView get(@PathVariable String groupId, @PathVariable String requestId,
                                      @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.get(groupId, actor.id(), requestId);
    }

    @GetMapping("/order-split/{reference}")
    public OrderSplitLookup.OrderSplitView lookupOrderSplit(@PathVariable String groupId, @PathVariable String reference,
                                                            @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.lookupOrderSplit(groupId, actor.id(), reference);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfitDistributionView propose(@PathVariable String groupId,
                                          @RequestBody ProposeProfitDistributionRequest request,
                                          @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.propose(groupId, actor.id(), request);
    }

    @PostMapping("/{requestId}/approve")
    public ProfitDistributionView approve(@PathVariable String groupId, @PathVariable String requestId,
                                          @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.approve(groupId, actor.id(), requestId);
    }

    @PostMapping("/{requestId}/reject")
    public ProfitDistributionView reject(@PathVariable String groupId, @PathVariable String requestId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return profitDistributionService.reject(groupId, actor.id(), requestId);
    }
}
