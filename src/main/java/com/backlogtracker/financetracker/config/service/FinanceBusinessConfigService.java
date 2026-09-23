package com.backlogtracker.financetracker.config.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.financetracker.config.domain.FinanceBusinessConfig;
import com.backlogtracker.financetracker.config.repository.FinanceBusinessConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinanceBusinessConfigService {

    private final FinanceBusinessConfigRepository repository;
    private final GroupService groupService;

    public FinanceBusinessConfig get(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findById(groupId)
                .orElse(FinanceBusinessConfig.builder().groupId(groupId).businessAccountConfigured(false).build());
    }

    public FinanceBusinessConfig setBusinessAccountConfigured(String groupId, String userId, boolean configured) {
        groupService.requireMember(groupId, userId);
        FinanceBusinessConfig cfg = repository.findById(groupId)
                .orElse(FinanceBusinessConfig.builder().groupId(groupId).build());
        cfg.setBusinessAccountConfigured(configured);
        return repository.save(cfg);
    }
}
