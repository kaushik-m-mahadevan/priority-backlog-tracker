package com.backlogtracker.ordertracker.master.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.ordertracker.master.dto.CostConfigChangeRequestView;
import com.backlogtracker.ordertracker.master.service.CostConfigChangeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/business-config/change-requests")
@RequiresUser
@RequiredArgsConstructor
public class CostConfigChangeController {

    private final CostConfigChangeService service;

    @GetMapping
    public List<CostConfigChangeRequestView> list(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return service.list(groupId, actor.id());
    }

    @PostMapping
    public CostConfigChangeRequestView propose(@PathVariable String groupId, @RequestBody ProposeRequest request,
                                               @AuthenticationPrincipal AuthUser actor) {
        return service.propose(groupId, actor.id(), request.overheadPercentage(), request.profitMarginPercentage(),
                request.hourlyWage());
    }

    @PostMapping("/{requestId}/approve")
    public CostConfigChangeRequestView approve(@PathVariable String groupId, @PathVariable String requestId,
                                               @AuthenticationPrincipal AuthUser actor) {
        return service.approve(groupId, actor.id(), requestId);
    }

    @PostMapping("/{requestId}/reject")
    public CostConfigChangeRequestView reject(@PathVariable String groupId, @PathVariable String requestId,
                                              @AuthenticationPrincipal AuthUser actor) {
        return service.reject(groupId, actor.id(), requestId);
    }

    public record ProposeRequest(double overheadPercentage, double profitMarginPercentage, double hourlyWage) {
    }
}
