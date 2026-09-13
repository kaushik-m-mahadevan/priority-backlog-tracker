package com.backlogtracker.backlogtracker.archive.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest;
import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest.Status;
import com.backlogtracker.backlogtracker.archive.repository.ArchiveRequestRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * Archiving an <em>active</em> item is a group decision (design: archive approval).
 * A member raises a request; it needs every current member's approval to go through,
 * and a single rejection kills it. {@code RESOLVED} / {@code REJECTED} are unaffected.
 */
@Service
@RequiredArgsConstructor
public class ArchiveRequestService {

    private final ArchiveRequestRepository requests;
    private final ItemRepository items;
    private final GroupService groupService;
    private final ArchiveService archiveService;
    private final NotificationService notificationService;

    public ArchiveRequest create(String itemId, AuthUser actor, String note) {
        Item item = items.findById(itemId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        Group g = groupService.requireMember(item.getGroupId(), actor.id());

        if (requests.existsByItemIdAndStatus(itemId, Status.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This item already has an open archive request");
        }

        ArchiveRequest req = requests.save(ArchiveRequest.builder()
                .itemId(itemId)
                .groupId(g.getId())
                .itemTitle(item.getTitle())
                .requestedByUserId(actor.id())
                .requestedByName(actor.name())
                .note(note == null || note.isBlank() ? null : note.trim())
                .status(Status.PENDING)
                .approvedByUserIds(new ArrayList<>(List.of(actor.id())))
                .build());

        req = evaluate(req, g);
        if (req.getStatus() == Status.PENDING) {
            notificationService.noticeArchiveRequest(req, others(g, actor.id()));
        }
        return req;
    }

    public ArchiveRequest approve(String id, AuthUser actor) {
        ArchiveRequest req = pending(id);
        Group g = groupService.requireMember(req.getGroupId(), actor.id());
        if (!req.hasApproval(actor.id())) {
            req.getApprovedByUserIds().add(actor.id());
            req = requests.save(req);
        }
        ArchiveRequest result = evaluate(req, g);
        if (result.getStatus() == Status.PENDING) {
            notificationService.markArchiveVoteCast(id, actor.id(), true);
        }
        return result;
    }

    public ArchiveRequest reject(String id, AuthUser actor) {
        ArchiveRequest req = pending(id);
        Group g = groupService.requireMember(req.getGroupId(), actor.id());
        req.setStatus(Status.REJECTED);
        req.setRejectedByUserId(actor.id());
        req.setRejectedByName(actor.name());
        req.setDecidedAt(Instant.now());
        req = requests.save(req);
        notificationService.resolveArchiveRequest(req, false, new ArrayList<>(g.getMemberIds()));
        return req;
    }

    public List<ArchiveRequest> pendingForGroup(String groupId, AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return requests.findByGroupIdAndStatus(groupId, Status.PENDING);
    }

    /** Grant the request when every current member has approved. */
    private ArchiveRequest evaluate(ArchiveRequest req, Group g) {
        List<String> members = g.getMemberIds();
        if (!new ArrayList<>(req.getApprovedByUserIds()).containsAll(members)) {
            return req;
        }
        archiveService.completeApproved(req.getItemId(), req.getRequestedByUserId());
        req.setStatus(Status.APPROVED);
        req.setDecidedAt(Instant.now());
        req = requests.save(req);
        if (members.size() > 1) {
            notificationService.resolveArchiveRequest(req, true, new ArrayList<>(members));
        }
        return req;
    }

    private ArchiveRequest pending(String id) {
        ArchiveRequest req = requests.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive request not found"));
        if (req.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already " + req.getStatus());
        }
        return req;
    }

    private static List<String> others(Group g, String exclude) {
        List<String> ids = new ArrayList<>(g.getMemberIds());
        ids.remove(exclude);
        return ids;
    }
}
