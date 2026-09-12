package com.backlogtracker.group.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.group.domain.Group;
import com.backlogtracker.group.dto.CreateGroupRequest;
import com.backlogtracker.group.dto.GroupView;
import com.backlogtracker.group.dto.RenameGroupRequest;
import com.backlogtracker.group.service.GroupService;
import com.backlogtracker.notification.dto.InviteRequest;
import com.backlogtracker.notification.service.NotificationService;
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
    private final NotificationService notificationService;

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

    /** Rename the group. Any member may do it. */
    @PatchMapping("/{id}")
    public GroupView rename(@PathVariable String id,
                            @Valid @RequestBody RenameGroupRequest request,
                            @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.rename(id, actor.id(), request.name()));
    }

    /** Invite an existing user (by email or @handle) — they get a notification to accept. */
    @PostMapping("/{id}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public void invite(@PathVariable String id,
                       @Valid @RequestBody InviteRequest request,
                       @AuthenticationPrincipal AuthUser actor) {
        notificationService.createGroupInvite(id, request.to(), actor);
    }

    /** Leave. If the caller was the last member the group and its items are deleted. */
    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        groupService.leave(id, actor.id());
    }

    /** Add a category to this group's own list. Any member may. */
    @PostMapping("/{id}/categories")
    public GroupView addCategory(@PathVariable String id, @RequestBody NameRequest request,
                                 @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.addCategory(id, actor.id(), request.name()));
    }

    /** Remove a category. 409 if items still use it without a {@code reassignTo}. */
    @DeleteMapping("/{id}/categories/{name}")
    public GroupView removeCategory(@PathVariable String id, @PathVariable String name,
                                    @RequestParam(required = false) String reassignTo,
                                    @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.removeCategory(id, actor.id(), name, reassignTo));
    }

    public record NameRequest(String name) {
    }
}
