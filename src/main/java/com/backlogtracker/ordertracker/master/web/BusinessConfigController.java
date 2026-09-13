package com.backlogtracker.ordertracker.master.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig.MandatoryItemType;
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
        return service.update(groupId, actor.id(), request.overheadPercentage(),
                request.profitMarginPercentage(), request.currency(),
                request.mandatoryItemTypes(), request.workStages());
    }

    public record UpdateRequest(double overheadPercentage, double profitMarginPercentage, String currency,
                                List<MandatoryItemType> mandatoryItemTypes, List<WorkStageType> workStages) {
    }
}
