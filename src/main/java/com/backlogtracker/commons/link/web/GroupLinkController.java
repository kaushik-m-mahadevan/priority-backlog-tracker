package com.backlogtracker.commons.link.web;

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

import com.backlogtracker.commons.link.dto.GroupLinkProposalView;
import com.backlogtracker.commons.link.dto.GroupLinkView;
import com.backlogtracker.commons.link.dto.LinkGroupRequest;
import com.backlogtracker.commons.link.service.GroupLinkProposalService;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Generic cross-applet group linking (design: platform integration follow-up) — see
 *  {@link com.backlogtracker.commons.link.domain.GroupLink}. */
@RestController
@RequestMapping("/api/groups/{id}")
@RequiresUser
@RequiredArgsConstructor
public class GroupLinkController {

    private final GroupLinkService linkService;
    private final GroupLinkProposalService proposalService;

    /** The group linked to {@code id} for the given other applet, if any. */
    @GetMapping("/links/{otherAppletKey}")
    public GroupLinkView get(@PathVariable String id, @PathVariable String otherAppletKey,
                             @AuthenticationPrincipal AuthUser actor) {
        return new GroupLinkView(linkService.linkedGroupId(id, actor.id(), otherAppletKey).orElse(null));
    }

    /** Links immediately with no approval step — only ever used by the Setup Wizard, whose
     *  freshly-created, still-empty group has nothing to gate (mb-14/mb-23). Every other
     *  caller goes through {@code /link-proposals} instead. */
    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public void link(@PathVariable String id, @Valid @RequestBody LinkGroupRequest request,
                     @AuthenticationPrincipal AuthUser actor) {
        linkService.link(id, actor.id(), request.groupId(), request.inviteAllMembers());
    }

    @DeleteMapping("/links/{otherAppletKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String id, @PathVariable String otherAppletKey,
                       @AuthenticationPrincipal AuthUser actor) {
        linkService.unlink(id, actor.id(), otherAppletKey);
    }

    /** mb-14: the manual link path — gated behind unanimous approval from {@code id}'s own
     *  members instead of linking outright. */
    @GetMapping("/link-proposals")
    public List<GroupLinkProposalView> listProposals(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return proposalService.list(id, actor.id());
    }

    @PostMapping("/link-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupLinkProposalView propose(@PathVariable String id, @Valid @RequestBody ProposeLinkRequest request,
                                         @AuthenticationPrincipal AuthUser actor) {
        return proposalService.propose(id, actor.id(), request.groupId(), request.intoCurrentEmails(), request.intoTargetEmails());
    }

    @PostMapping("/link-proposals/{requestId}/approve")
    public GroupLinkProposalView approve(@PathVariable String id, @PathVariable String requestId,
                                         @AuthenticationPrincipal AuthUser actor) {
        return proposalService.approve(id, actor.id(), requestId);
    }

    @PostMapping("/link-proposals/{requestId}/reject")
    public GroupLinkProposalView reject(@PathVariable String id, @PathVariable String requestId,
                                        @AuthenticationPrincipal AuthUser actor) {
        return proposalService.reject(id, actor.id(), requestId);
    }

    public record ProposeLinkRequest(String groupId, List<String> intoCurrentEmails, List<String> intoTargetEmails) {
    }
}
