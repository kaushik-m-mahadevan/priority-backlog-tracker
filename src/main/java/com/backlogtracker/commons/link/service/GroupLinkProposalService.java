package com.backlogtracker.commons.link.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.event.ApprovalRequestInvalidatedEvent;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.dto.GroupLinkProposalView;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * mb-14: gates the manual "link an existing group" path behind the same unanimous-approval
 * mechanics as a cost-config change (design decision: linking a business to another
 * applet's group exposes that group's data here, same magnitude of change) — unlike the
 * Setup Wizard's own {@link GroupLinkService#link(String, String, String, boolean)} call,
 * which links immediately with no approval step (a brand-new, still-empty group has nothing
 * to gate).
 *
 * <p>Unanimity is required only from {@code groupId}'s own members — the group whose data is
 * about to be exposed to a new link, same "one group's members decide" shape as every other
 * {@link ApprovalService} caller. The target group's members aren't consulted here; they
 * only see the effect once the (optional, curated) invite reaches them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupLinkProposalService {

    private static final String KIND_PREFIX = "grouplink:";

    private final ApprovalService approvalService;
    private final GroupService groupService;
    private final GroupLinkService groupLinkService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final NotificationService notificationService;

    public GroupLinkProposalView propose(String groupId, String userId, String targetGroupId,
                                         List<String> intoCurrentEmails, List<String> intoTargetEmails) {
        Group group = groupLinkService.requireLinkable(groupId, userId, targetGroupId).a();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupIdB", targetGroupId);
        payload.put("intoCurrentEmails", intoCurrentEmails == null ? List.of() : intoCurrentEmails);
        payload.put("intoTargetEmails", intoTargetEmails == null ? List.of() : intoTargetEmails);

        ApprovalRequest approval = approvalService.propose(groupId, userId, kind(groupId), payload);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            applyLink(approval);
        } else {
            notificationOrchestrator.notifyOtherMembersActionable(group, userId, NotificationType.GROUP_LINK_PROPOSED,
                    "Group link", "A proposal to link this group to another applet's group is waiting for your approval — "
                            + "approve or reject it in Connections.", null, approval.getId());
        }
        return view(approval, group);
    }

    public GroupLinkProposalView approve(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        ApprovalRequest approval = approvalService.approve(groupId, userId, requestId);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            applyLink(approval);
            notificationService.resolveByReference(NotificationType.GROUP_LINK_PROPOSED, requestId, true);
        } else {
            notificationService.resolveOneByReference(NotificationType.GROUP_LINK_PROPOSED, requestId, userId, true);
        }
        return view(approval, group);
    }

    public GroupLinkProposalView reject(String groupId, String userId, String requestId) {
        Group group = groupService.requireMember(groupId, userId);
        ApprovalRequest approval = approvalService.reject(groupId, userId, requestId);
        notificationService.resolveByReference(NotificationType.GROUP_LINK_PROPOSED, requestId, false);
        return view(approval, group);
    }

    public List<GroupLinkProposalView> list(String groupId, String userId) {
        Group group = groupService.requireMember(groupId, userId);
        return approvalService.list(groupId, userId, kind(groupId)).stream()
                .map(r -> view(r, group))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void applyLink(ApprovalRequest approval) {
        String groupId = approval.getGroupId();
        String targetGroupId = (String) approval.getPayload().get("groupIdB");
        List<String> intoCurrentEmails = (List<String>) approval.getPayload().getOrDefault("intoCurrentEmails", List.of());
        List<String> intoTargetEmails = (List<String>) approval.getPayload().getOrDefault("intoTargetEmails", List.of());
        String proposerId = approval.getProposedByUserId();

        groupLinkService.link(groupId, proposerId, targetGroupId, false);
        intoCurrentEmails.forEach(email -> groupLinkService.inviteMember(groupId, proposerId, email));
        intoTargetEmails.forEach(email -> groupLinkService.inviteMember(targetGroupId, proposerId, email));
    }

    /** A member leaving mid-approval invalidates the underlying ApprovalRequest generically
     *  — just notify the proposer, mirroring every other approval-backed flow's listener. */
    @EventListener
    public void onApprovalInvalidated(ApprovalRequestInvalidatedEvent event) {
        if (!event.kind().startsWith(KIND_PREFIX)) {
            return;
        }
        notificationService.resolveByReference(NotificationType.GROUP_LINK_PROPOSED, event.requestId(), false);
        notificationService.info(event.proposedByUserId(), NotificationType.GROUP_LINK_INVALIDATED,
                "Group link", "Your proposed group link was cancelled because a member left the group mid-approval. "
                        + "You can propose it again.", null);
        log.info("Group link proposal {} invalidated", event.requestId());
    }

    private static String kind(String groupId) {
        return KIND_PREFIX + groupId;
    }

    private static GroupLinkProposalView view(ApprovalRequest approval, Group group) {
        return GroupLinkProposalView.of(approval, group.getMemberIds());
    }
}
