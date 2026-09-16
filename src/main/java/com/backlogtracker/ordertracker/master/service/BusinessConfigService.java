package com.backlogtracker.ordertracker.master.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
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
                                 List<WorkStageType> workStages) {
        BusinessConfig cfg = get(groupId, userId); // ensures membership + seeds if absent
        cfg.setCurrency(currency);
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

    /** Called once, immediately after a business is created, right before the frontend
     *  routes into the setup wizard — flips this specific business into "setup pending"
     *  so {@link #get} keeps reporting it as incomplete until {@link #completeSetup}.
     *  Every other business (created before this feature existed, or reached through any
     *  other path) keeps {@code defaultsFor}'s {@code true} and is never gated — design
     *  decision: no retroactive setup prompt for businesses that already existed. */
    public BusinessConfig startSetup(String groupId, String userId) {
        BusinessConfig cfg = get(groupId, userId); // ensures membership + seeds if absent
        cfg.setSetupComplete(false);
        return repository.save(cfg);
    }

    public BusinessConfig completeSetup(String groupId, String userId) {
        BusinessConfig cfg = get(groupId, userId);
        cfg.setSetupComplete(true);
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
