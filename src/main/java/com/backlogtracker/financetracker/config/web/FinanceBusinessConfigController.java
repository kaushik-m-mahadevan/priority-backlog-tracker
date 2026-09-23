package com.backlogtracker.financetracker.config.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.financetracker.config.domain.FinanceBusinessConfig;
import com.backlogtracker.financetracker.config.service.FinanceBusinessConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financetracker/groups/{groupId}/business-config")
@RequiresUser
@RequiredArgsConstructor
public class FinanceBusinessConfigController {

    private final FinanceBusinessConfigService service;

    @GetMapping
    public FinanceBusinessConfig get(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.get(groupId, actor.id());
    }

    @PutMapping
    public FinanceBusinessConfig update(@PathVariable String groupId, @RequestBody UpdateRequest request,
                                        @AuthenticationPrincipal AuthUser actor) {
        return service.setBusinessAccountConfigured(groupId, actor.id(), request.businessAccountConfigured());
    }

    public record UpdateRequest(boolean businessAccountConfigured) {
    }
}
