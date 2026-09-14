package com.backlogtracker.ordertracker.master.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.MandatoryItemType;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.WorkStageType;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessConfigService {

    private final BusinessConfigRepository repository;
    private final GroupService groupService;
    private final SecureRandom random = new SecureRandom();

    public BusinessConfig get(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findById(groupId).orElseGet(() -> seed(groupId));
    }

    /** Overhead % and profit margin % are deliberately absent here — they change what
     *  every existing order costs, so they go only through {@code CostConfigChangeService}'s
     *  unanimous-approval flow (platform integration decision). Everything else about a
     *  business's config is low-stakes enough for any member to change freely. */
    public BusinessConfig update(String groupId, String userId, String currency,
                                 List<MandatoryItemType> mandatoryItemTypes,
                                 List<WorkStageType> workStages) {
        BusinessConfig cfg = get(groupId, userId); // ensures membership + seeds if absent
        cfg.setCurrency(currency);
        cfg.setMandatoryItemTypes(mandatoryItemTypes);
        cfg.setWorkStages(workStages);
        return repository.save(cfg);
    }

    /** Used only by {@code CostConfigChangeService} once a proposal is unanimously approved. */
    BusinessConfig applyCostConfig(String groupId, double overheadPercentage, double profitMarginPercentage) {
        BusinessConfig cfg = repository.findById(groupId).orElseGet(() -> seed(groupId));
        cfg.setOverheadPercentage(overheadPercentage);
        cfg.setProfitMarginPercentage(profitMarginPercentage);
        return repository.save(cfg);
    }

    private BusinessConfig seed(String groupId) {
        BusinessConfig cfg = BusinessConfig.defaultsFor(groupId);
        String individual = randomTwoDigit();
        String bulk = randomTwoDigit();
        while (bulk.equals(individual)) {
            bulk = randomTwoDigit();
        }
        cfg.setIndividualOrderTypeCode(individual);
        cfg.setBulkOrderTypeCode(bulk);
        return repository.save(cfg);
    }

    private String randomTwoDigit() {
        return String.format("%02d", random.nextInt(100));
    }
}
