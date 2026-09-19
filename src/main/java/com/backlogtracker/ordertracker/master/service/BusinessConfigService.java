package com.backlogtracker.ordertracker.master.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.ordertracker.OrderCodeWidths;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.WorkStageType;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessConfigService {

    private final BusinessConfigRepository repository;
    private final GroupService groupService;
    private final RandomCodeAssigner codeAssigner;

    public BusinessConfig get(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findById(groupId).orElseGet(() -> seed(groupId));
    }

    /** Overhead %, profit margin %, and hourly wage are deliberately absent here — they
     *  change what every existing order costs, so they go only through
     *  {@code CostConfigChangeService}'s unanimous-approval flow (platform integration
     *  decision). Delivery-buffer tiers are logistics guesses, not pricing, so they stay
     *  here as freely editable (round 5 delivery-estimate redesign) alongside everything
     *  else that's low-stakes enough for any member to change freely. */
    public BusinessConfig update(String groupId, String userId, String currency,
                                 List<WorkStageType> workStages, int deliveryBufferSameCityDays,
                                 int deliveryBufferSameStateDays, int deliveryBufferOtherStateDays,
                                 int deliveryBufferInternationalDays) {
        BusinessConfig cfg = get(groupId, userId); // ensures membership + seeds if absent
        cfg.setCurrency(currency);
        cfg.setWorkStages(workStages);
        cfg.setDeliveryBufferSameCityDays(deliveryBufferSameCityDays);
        cfg.setDeliveryBufferSameStateDays(deliveryBufferSameStateDays);
        cfg.setDeliveryBufferOtherStateDays(deliveryBufferOtherStateDays);
        cfg.setDeliveryBufferInternationalDays(deliveryBufferInternationalDays);
        return repository.save(cfg);
    }

    /** Used only by {@code CostConfigChangeService} once a proposal is unanimously approved.
     *  {@code hourlyWageConfirmed} only flips true when the incoming wage actually differs
     *  from what's on file — every cost-config propose (including the setup wizard's
     *  overhead/margin-only step) resends the current wage untouched, and that must NOT
     *  silently "confirm" a wage nobody actually agreed on. Once confirmed, stays confirmed
     *  even if a later change happens to round-trip back to the same number. */
    BusinessConfig applyCostConfig(String groupId, double overheadPercentage, double profitMarginPercentage,
                                   double hourlyWage) {
        BusinessConfig cfg = repository.findById(groupId).orElseGet(() -> seed(groupId));
        cfg.setOverheadPercentage(overheadPercentage);
        cfg.setProfitMarginPercentage(profitMarginPercentage);
        if (hourlyWage != cfg.getHourlyWage()) {
            cfg.setHourlyWageConfirmed(true);
        }
        cfg.setHourlyWage(hourlyWage);
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
        String individual = codeAssigner.randomDigits(OrderCodeWidths.ORDER_TYPE_CODE_DIGITS);
        String bulk = codeAssigner.randomDigits(OrderCodeWidths.ORDER_TYPE_CODE_DIGITS);
        while (bulk.equals(individual)) {
            bulk = codeAssigner.randomDigits(OrderCodeWidths.ORDER_TYPE_CODE_DIGITS);
        }
        cfg.setIndividualOrderTypeCode(individual);
        cfg.setBulkOrderTypeCode(bulk);
        return repository.save(cfg);
    }
}
