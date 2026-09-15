package com.backlogtracker.commons.link.web;

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

import com.backlogtracker.commons.link.dto.GroupLinkView;
import com.backlogtracker.commons.link.dto.LinkGroupRequest;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Generic cross-applet group linking (design: platform integration follow-up) — see
 *  {@link com.backlogtracker.commons.link.domain.GroupLink}. */
@RestController
@RequestMapping("/api/groups/{id}/links")
@RequiresUser
@RequiredArgsConstructor
public class GroupLinkController {

    private final GroupLinkService linkService;

    /** The group linked to {@code id} for the given other applet, if any. */
    @GetMapping("/{otherAppletKey}")
    public GroupLinkView get(@PathVariable String id, @PathVariable String otherAppletKey,
                             @AuthenticationPrincipal AuthUser actor) {
        return new GroupLinkView(linkService.linkedGroupId(id, otherAppletKey).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void link(@PathVariable String id, @Valid @RequestBody LinkGroupRequest request,
                     @AuthenticationPrincipal AuthUser actor) {
        linkService.link(id, actor.id(), request.groupId());
    }

    @DeleteMapping("/{otherAppletKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String id, @PathVariable String otherAppletKey,
                       @AuthenticationPrincipal AuthUser actor) {
        linkService.unlink(id, actor.id(), otherAppletKey);
    }
}
