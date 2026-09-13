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
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.service.CreatorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordertracker/groups/{groupId}/creators")
@RequiresUser
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;

    /** Everyone with a profile in this group's business — for assignment pickers. */
    @GetMapping
    public List<Creator> all(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return creatorService.all(groupId, actor.id());
    }

    /** null if the caller hasn't set up a profile for this group yet. */
    @GetMapping("/me")
    public Creator myProfile(@PathVariable String groupId, @AuthenticationPrincipal AuthUser actor) {
        return creatorService.myProfile(groupId, actor.id());
    }

    @PutMapping("/me")
    public Creator upsertMyProfile(@PathVariable String groupId, @RequestBody UpsertProfileRequest request,
                                   @AuthenticationPrincipal AuthUser actor) {
        return creatorService.upsertMyProfile(groupId, actor.id(), actor.name(),
                request.baseLocation(), request.hoursAvailablePerDay());
    }

    public record UpsertProfileRequest(String baseLocation, double hoursAvailablePerDay) {
    }
}
