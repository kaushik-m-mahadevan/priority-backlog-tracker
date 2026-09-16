package com.backlogtracker.commons.group.web;

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
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.dto.CreateGroupRequest;
import com.backlogtracker.commons.group.dto.GroupView;
import com.backlogtracker.commons.group.dto.RenameGroupRequest;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.dto.InviteRequest;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

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

    /** Groups the caller belongs to, scoped to one applet. Defaults to Backlog Tracker so
     *  every existing caller (no query param) keeps seeing exactly what it saw before Order
     *  Tracker existed. */
    @GetMapping
    public List<GroupView> mine(@RequestParam(required = false) String appletKey,
                                @AuthenticationPrincipal AuthUser actor) {
        return groupService.myGroups(actor.id(), resolveAppletKey(appletKey)).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView create(@Valid @RequestBody CreateGroupRequest request,
                            @RequestParam(required = false) String appletKey,
                            @AuthenticationPrincipal AuthUser actor) {
        return view(groupService.create(request.name(), actor.id(), resolveAppletKey(appletKey)));
    }

    private static String resolveAppletKey(String appletKey) {
        if (appletKey == null || appletKey.isBlank()) {
            return Group.APPLET_BACKLOG_TRACKER;
        }
        if (!appletKey.equals(Group.APPLET_BACKLOG_TRACKER) && !appletKey.equals(Group.APPLET_ORDER_TRACKER)
                && !appletKey.equals(Group.APPLET_FINANCE_TRACKER) && !appletKey.equals(Group.APPLET_MATERIAL_INVENTORY)
                && !appletKey.equals(Group.APPLET_PRODUCT_CATALOG)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown appletKey");
        }
        return appletKey;
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

    // Categories moved to config.web.GroupCategoryController (backlogtracker-owned) at
    // the same URL prefix — a group's category list is applet data, not commons.
}
