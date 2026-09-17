package com.backlogtracker.commons.link.service;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.event.GroupDeletedEvent;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.domain.GroupLink;
import com.backlogtracker.commons.link.repository.GroupLinkRepository;

import lombok.RequiredArgsConstructor;

/**
 * Generic cross-applet group linking (design: platform integration follow-up) — see
 * {@link GroupLink} for the shape and the 1:1-per-pairing guarantee. Depends on
 * {@link GroupService} only one-way (for membership checks); cleanup when a linked group
 * is deleted happens via {@link GroupDeletedEvent} instead of a direct dependency back,
 * so the two services never form a construction cycle.
 */
@Service
@RequiredArgsConstructor
public class GroupLinkService {

    private final GroupLinkRepository links;
    private final GroupService groupService;

    /** Links {@code groupIdA} to {@code groupIdB}. The caller must be a member of both —
     *  linking two groups you don't belong to would let you snoop on membership/appletKey
     *  of a group you have no business seeing. Rejects same-applet pairs (linking is for
     *  crossing a permission boundary, not within one) and either side already being
     *  linked to that other applet. */
    public GroupLink link(String groupIdA, String userId, String groupIdB) {
        Group a = groupService.requireMember(groupIdA, userId);
        Group b = groupService.requireMember(groupIdB, userId);
        if (a.getAppletKey().equals(b.getAppletKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only link groups from two different applets");
        }
        if (links.findByGroupIdAAndAppletKeyB(groupIdA, b.getAppletKey()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This group is already linked to a " + b.getAppletKey() + " group");
        }
        if (links.findByGroupIdBAndAppletKeyA(groupIdB, a.getAppletKey()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That group is already linked to a " + a.getAppletKey() + " group");
        }
        try {
            return links.save(GroupLink.builder()
                    .groupIdA(groupIdA).appletKeyA(a.getAppletKey())
                    .groupIdB(groupIdB).appletKeyB(b.getAppletKey())
                    .build());
        } catch (DuplicateKeyException e) {
            // Two concurrent link attempts raced past the checks above — the unique index
            // is the real guard; the pre-checks are just a friendlier error message.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One of these groups was just linked elsewhere");
        }
    }

    /** Removes whichever link (if any) connects {@code groupId} to a group of
     *  {@code otherAppletKey}, from either the A or B side. */
    public void unlink(String groupId, String userId, String otherAppletKey) {
        groupService.requireMember(groupId, userId);
        links.findByGroupIdAAndAppletKeyB(groupId, otherAppletKey).ifPresent(links::delete);
        links.findByGroupIdBAndAppletKeyA(groupId, otherAppletKey).ifPresent(links::delete);
    }

    /** The id of the group linked to {@code groupId} for the given other applet, if any —
     *  checked from either side since the caller may hold either end of the link. Requires
     *  membership in {@code groupId} first — without this, any authenticated user could probe
     *  an arbitrary group id to learn what other-applet group it's linked to (IDOR). */
    public Optional<String> linkedGroupId(String groupId, String userId, String otherAppletKey) {
        groupService.requireMember(groupId, userId);
        return links.findByGroupIdAAndAppletKeyB(groupId, otherAppletKey).map(GroupLink::getGroupIdB)
                .or(() -> links.findByGroupIdBAndAppletKeyA(groupId, otherAppletKey).map(GroupLink::getGroupIdA));
    }

    /** Cleans up any link referencing a group that's just been deleted (its last member
     *  left) — an orphaned link pointing at a group that no longer exists would otherwise
     *  silently block that appletKey pairing from ever being used again. */
    @EventListener
    public void onGroupDeleted(GroupDeletedEvent event) {
        links.deleteByGroupIdAOrGroupIdB(event.groupId(), event.groupId());
    }
}
