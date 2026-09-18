package com.backlogtracker.backlogtracker.archive.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest;
import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest.Status;
import com.backlogtracker.backlogtracker.archive.repository.ArchiveRequestRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.event.MemberLeftGroupEvent;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Archiving an <em>active</em> item is a group decision (design: archive approval).
 * A member raises a request; it needs every current member's approval to go through,
 * and a single rejection kills it. {@code RESOLVED} / {@code REJECTED} are unaffected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveRequestService {

    // Higher than CostConfigChangeService's equivalent retry cap: that request is contended
    // by at most a handful of group members approving once each, but so is this one in the
    // common case — the higher cap mainly protects a worst-case stress scenario (many
    // members approving within the same instant) from exhausting retries under contention,
    // same reasoning as TransferRequestService's own bumped retry cap.
    private static final int MAX_APPROVE_RETRIES = 20;

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

    /** Retries on a lost-update race (two members approving the same request at once) by
     *  re-reading the latest approvedByUserIds and reapplying this approval on top of it,
     *  rather than silently losing whichever save lost the race (same pattern as
     *  {@code CostConfigChangeService.approve}/{@code TransferRequestService.fulfill}). The
     *  archive-completion side effect only runs after a save actually succeeds, so a
     *  retried-but-ultimately-successful save can't archive the item twice. */
    public ArchiveRequest approve(String id, AuthUser actor) {
        Group group = null;
        for (int attempt = 1; attempt <= MAX_APPROVE_RETRIES; attempt++) {
            ArchiveRequest req = pending(id);
            group = groupService.requireMember(req.getGroupId(), actor.id());
            if (!req.hasApproval(actor.id())) {
                req.getApprovedByUserIds().add(actor.id());
            }
            boolean unanimous = req.getApprovedByUserIds().containsAll(group.getMemberIds());
            if (unanimous) {
                req.setStatus(Status.APPROVED);
                req.setDecidedAt(Instant.now());
            }
            try {
                req = requests.save(req);
                if (unanimous) {
                    finishApproval(req, group);
                } else {
                    notificationService.markArchiveVoteCast(id, actor.id(), true);
                }
                return req;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_APPROVE_RETRIES) {
                    throw e;
                }
                log.info("Archive-request approve race on request {} — retrying (attempt {})", id, attempt);
            }
        }
        throw new IllegalStateException("unreachable");
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

    /** Grant the request when every current member has approved (only reachable from
     *  {@link #create} — a freshly-inserted document has no concurrent contention, so this
     *  doesn't need the retry loop {@link #approve} uses). */
    private ArchiveRequest evaluate(ArchiveRequest req, Group g) {
        List<String> members = g.getMemberIds();
        if (!new ArrayList<>(req.getApprovedByUserIds()).containsAll(members)) {
            return req;
        }
        req.setStatus(Status.APPROVED);
        req.setDecidedAt(Instant.now());
        req = requests.save(req);
        finishApproval(req, g);
        return req;
    }

    /** Runs the archive-completion side effect exactly once, only after a save has already
     *  recorded the request as APPROVED — never called speculatively before a save
     *  succeeds, so a retried approve() can't archive the same item twice. */
    private void finishApproval(ArchiveRequest req, Group group) {
        archiveService.completeApproved(req.getItemId(), req.getRequestedByUserId());
        if (group.getMemberIds().size() > 1) {
            notificationService.resolveArchiveRequest(req, true, new ArrayList<>(group.getMemberIds()));
        }
    }

    /** A member leaving mid-vote can reasonably change how the remaining members would
     *  have voted, so every pending archive request in that group is cancelled outright
     *  rather than silently resolved on whoever's left (same pattern as
     *  {@code CostConfigChangeService.onMemberLeft} / {@code ApprovalService.onMemberLeft})
     *  — otherwise a departure could be misread as implicit consent: the remaining
     *  approvals might already satisfy unanimity among the now-smaller membership. */
    @EventListener
    public void onMemberLeft(MemberLeftGroupEvent event) {
        requests.findByGroupIdAndStatus(event.groupId(), Status.PENDING).forEach(req -> {
            req.setStatus(Status.INVALIDATED);
            req.setDecidedAt(Instant.now());
            requests.save(req);
            notificationService.info(req.getRequestedByUserId(), NotificationType.ARCHIVE_REQUEST_INVALIDATED,
                    "Your request to archive “" + req.getItemTitle() + "” was cancelled because a "
                            + "member left the group mid-approval. You can raise it again.");
            log.info("Archive request {} invalidated — member {} left group {}",
                    req.getId(), event.userId(), event.groupId());
        });
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
