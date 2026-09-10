package com.backlogtracker.group.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.group.domain.Group;
import com.backlogtracker.group.dto.CreateGroupRequest;
import com.backlogtracker.group.dto.GroupView;
import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiresUser
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    private GroupView view(Group g) {
        return GroupView.of(g, groupService.members(g));
    }

    /** Groups the caller belongs to. */
    @GetMapping
    public List<GroupView> mine(@AuthenticationPrincipal AuthUser actor) {
        return groupService.myGroups(actor.id()).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView create(@Valid @RequestBody CreateGroupRequest request,
                            @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.create(request.name(), actor.id()));
    }

    @GetMapping("/{id}")
    public GroupView get(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.requireMember(id, actor.id()));
    }

    /** Leave. If the caller was the last member the group and its items are deleted. */
    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        groupService.leave(id, actor.id());
    }
}
