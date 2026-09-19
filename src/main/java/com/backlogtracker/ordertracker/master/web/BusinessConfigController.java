package com.backlogtracker.ordertracker.master.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.WorkStageType;
import com.backlogtracker.ordertracker.master.service.BusinessConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/business-config")
@RequiresUser
@RequiredArgsConstructor
public class BusinessConfigController {

    private final BusinessConfigService service;

    @GetMapping
    public BusinessConfig get(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.get(groupId, actor.id());
    }

    @PutMapping
    public BusinessConfig update(@PathVariable String groupId, @RequestBody UpdateRequest request,
                                 @AuthenticationPrincipal AuthUser actor) {
        return service.update(groupId, actor.id(), request.currency(), request.workStages(),
                request.deliveryBufferSameCityDays(), request.deliveryBufferSameStateDays(),
                request.deliveryBufferOtherStateDays(), request.deliveryBufferInternationalDays());
    }

    @PostMapping("/setup/start")
    public BusinessConfig startSetup(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.startSetup(groupId, actor.id());
    }

    @PostMapping("/setup/complete")
    public BusinessConfig completeSetup(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.completeSetup(groupId, actor.id());
    }

    /** overheadPercentage/profitMarginPercentage/hourlyWage are not here on purpose — see
     *  CostConfigChangeController for changing those, which requires unanimous approval.
     *  Delivery-buffer tiers are logistics guesses, not pricing, so they're freely editable
     *  here (round 5 delivery-estimate redesign). */
    public record UpdateRequest(String currency, List<WorkStageType> workStages,
                                int deliveryBufferSameCityDays, int deliveryBufferSameStateDays,
                                int deliveryBufferOtherStateDays, int deliveryBufferInternationalDays) {
    }
}
