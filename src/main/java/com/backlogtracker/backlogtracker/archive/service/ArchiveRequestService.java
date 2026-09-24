package com.backlogtracker.backlogtracker.archive.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.archive.dto.ArchiveRequestView;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the generic {@link ApprovalService} to Backlog Tracker's own unanimous
 * archive-approval (qd-1) — archiving an <em>active</em> item is a group decision: a
 * member raises a request, every current member must approve it, and a single rejection
 * kills it. One item has at most one PENDING archive request at a time, {@code kind =
 * "backlogtracker:archive:<itemId>"}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveRequestService {

    private static final String KIND_PREFIX = "backlogtracker:archive:";

    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequests;
    private final ItemRepository items;
    private final GroupService groupService;
    private final ArchiveService archiveService;
    private final NotificationService notificationService;

    public ArchiveRequestView create(String itemId, AuthUser actor, String note) {
        Item item = items.findById(itemId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        Group group = groupService.requireMember(item.getGroupId(), actor.id());

        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("itemTitle", item.getTitle());
        payload.put("requestedByName", actor.name());
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        if (trimmedNote != null) {
            payload.put("note", trimmedNote);
        }

        ApprovalRequest approval = approvalService.propose(group.getId(), actor.id(), kind(itemId), payload);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            applyApproval(approval, group);
        } else {
            notificationService.noticeArchiveRequest(approval.getId(), group.getId(), itemId, item.getTitle(),
                    actor.name(), trimmedNote, others(group, actor.id()));
        }
        return ArchiveRequestView.of(approval);
    }

    /** Retries on a lost-update race (two members approving at once) via
     *  {@link ApprovalService#approve}'s own retry loop; the archive-completion side effect
     *  only runs after that returns an already-persisted APPROVED result, so a
     *  retried-but-ultimately-successful approval can't archive the item twice. */
    public ArchiveRequestView approve(String id, AuthUser actor) {
        ApprovalRequest existing = requireArchiveRequest(id);
        ApprovalRequest approval = approvalService.approve(existing.getGroupId(), actor.id(), id);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            Group group = groupService.requireMember(approval.getGroupId(), actor.id());
            applyApproval(approval, group);
        } else {
            notificationService.markArchiveVoteCast(id, actor.id(), true);
        }
        return ArchiveRequestView.of(approval);
    }

    public ArchiveRequestView reject(String id, AuthUser actor) {
        ApprovalRequest existing = requireArchiveRequest(id);
        ApprovalRequest approval = approvalService.reject(existing.getGroupId(), actor.id(), id);
        Group group = groupService.requireMember(approval.getGroupId(), actor.id());
        notificationService.resolveArchiveRequest(approval.getId(), approval.getGroupId(), itemId(approval),
                itemTitle(approval), false, actor.name(), new ArrayList<>(group.getMemberIds()));
        return ArchiveRequestView.of(approval);
    }

    public List<ArchiveRequestView> pendingForGroup(String groupId, AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return approvalRequests.findByGroupIdAndStatus(groupId, ApprovalStatus.PENDING).stream()
                .filter(r -> r.getKind().startsWith(KIND_PREFIX))
                .map(ArchiveRequestView::of)
                .toList();
    }

    /** Runs the archive-completion side effect exactly once — only ever reached right after
     *  {@link ApprovalService#propose}/{@link ApprovalService#approve} itself returns an
     *  APPROVED result, which under their own retry/pending-check guarantees happens at
     *  most once per request. */
    private void applyApproval(ApprovalRequest approval, Group group) {
        archiveService.completeApproved(itemId(approval), approval.getProposedByUserId());
        if (group.getMemberIds().size() > 1) {
            notificationService.resolveArchiveRequest(approval.getId(), approval.getGroupId(), itemId(approval),
                    itemTitle(approval), true, null, new ArrayList<>(group.getMemberIds()));
        }
    }

    /** The generic approval layer only announces an invalidation — sending the actual
     *  notification (which needs the item's title, not carried on the event itself) is a
     *  per-kind concern, mirroring OrderFinalizationService's/ProfitDistributionService's/
     *  CostConfigChangeService's own listeners for the same event. */
    @EventListener
    public void onApprovalInvalidated(ApprovalRequestInvalidatedEvent event) {
        if (!event.kind().startsWith(KIND_PREFIX)) {
            return;
        }
        approvalRequests.findById(event.requestId()).ifPresent(req -> {
            notificationService.info(event.proposedByUserId(), NotificationType.ARCHIVE_REQUEST_INVALIDATED,
                    "Archive request", "Your request to archive “" + itemTitle(req) + "” was cancelled because a "
                            + "member left the group mid-approval. You can raise it again.", null);
            log.info("Archive request {} invalidated", event.requestId());
        });
    }

    private ApprovalRequest requireArchiveRequest(String id) {
        ApprovalRequest req = approvalRequests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive request not found"));
        if (!req.getKind().startsWith(KIND_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive request not found");
        }
        return req;
    }

    private static String itemId(ApprovalRequest r) {
        return (String) r.getPayload().get("itemId");
    }

    private static String itemTitle(ApprovalRequest r) {
        return (String) r.getPayload().get("itemTitle");
    }

    private static String kind(String itemId) {
        return KIND_PREFIX + itemId;
    }

    private static List<String> others(Group g, String exclude) {
        List<String> ids = new ArrayList<>(g.getMemberIds());
        ids.remove(exclude);
        return ids;
    }
}
