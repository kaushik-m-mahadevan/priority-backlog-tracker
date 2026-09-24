package com.backlogtracker.commons.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.dto.GroupLinkProposalView;
import com.backlogtracker.commons.link.repository.GroupLinkRepository;
import com.backlogtracker.commons.link.service.GroupLinkProposalService;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

/** mb-14: the manual link path gated behind unanimous approval from the proposing group's
 *  own members, plus the curated invite list that only actually sends once approved. */
@SpringBootTest
class GroupLinkProposalServiceTest {

    @Autowired GroupService groupService;
    @Autowired GroupLinkService groupLinkService;
    @Autowired GroupLinkProposalService proposalService;
    @Autowired GroupRepository groups;
    @Autowired GroupLinkRepository links;
    @Autowired ApprovalRequestRepository approvalRequests;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    private String soleOwnerId;
    private String memberAId;
    private String memberBId;

    @BeforeEach
    void setUp() {
        soleOwnerId = users.save(User.builder().name("Sole Owner").email("linkprop-sole@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linkpropsole").build()).getId();
        memberAId = users.save(User.builder().name("Member A").email("linkprop-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linkpropa").build()).getId();
        memberBId = users.save(User.builder().name("Member B").email("linkprop-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linkpropb").build()).getId();
    }

    @AfterEach
    void cleanUp() {
        groups.findByMemberIdsContaining(soleOwnerId).forEach(g -> groups.delete(g));
        groups.findByMemberIdsContaining(memberAId).forEach(g -> groups.delete(g));
        groups.findByMemberIdsContaining(memberBId).forEach(g -> groups.delete(g));
        users.deleteById(soleOwnerId);
        users.deleteById(memberAId);
        users.deleteById(memberBId);
    }

    @Test
    void aSoloMemberGroupsProposalResolvesImmediatelyAndLinksRightAway() {
        Group business = groupService.create("Solo Business", soleOwnerId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Solo Finance", soleOwnerId, Group.APPLET_FINANCE_TRACKER);

        GroupLinkProposalView view = proposalService.propose(business.getId(), soleOwnerId, finance.getId(), List.of(), List.of());

        assertThat(view.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(groupLinkService.linkedGroupId(business.getId(), soleOwnerId, Group.APPLET_FINANCE_TRACKER))
                .contains(finance.getId());
    }

    @Test
    void aMultiMemberGroupsProposalStaysPendingUntilEveryMemberApprovesThenLinksAndInvites() {
        Group business = groupService.create("Multi Business", soleOwnerId, Group.APPLET_ORDER_TRACKER);
        business = groupService.addMember(business.getId(), memberAId);
        Group finance = groupService.create("Multi Finance", soleOwnerId, Group.APPLET_FINANCE_TRACKER);

        GroupLinkProposalView proposed = proposalService.propose(business.getId(), soleOwnerId, finance.getId(),
                List.of(), List.of("linkprop-b@x.test"));
        assertThat(proposed.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(groupLinkService.linkedGroupId(business.getId(), soleOwnerId, Group.APPLET_FINANCE_TRACKER)).isEmpty();

        GroupLinkProposalView resolved = proposalService.approve(business.getId(), memberAId, proposed.id());

        assertThat(resolved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(groupLinkService.linkedGroupId(business.getId(), soleOwnerId, Group.APPLET_FINANCE_TRACKER))
                .contains(finance.getId());
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(memberBId))
                .anySatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.GROUP_INVITE);
                    assertThat(n.getGroupId()).isEqualTo(finance.getId());
                });
    }

    @Test
    void rejectingLeavesTheGroupsUnlinked() {
        Group business = groupService.create("Rejected Business", soleOwnerId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(business.getId(), memberAId);
        Group finance = groupService.create("Rejected Finance", soleOwnerId, Group.APPLET_FINANCE_TRACKER);

        GroupLinkProposalView proposed = proposalService.propose(business.getId(), soleOwnerId, finance.getId(), List.of(), List.of());
        GroupLinkProposalView rejected = proposalService.reject(business.getId(), memberAId, proposed.id());

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(groupLinkService.linkedGroupId(business.getId(), soleOwnerId, Group.APPLET_FINANCE_TRACKER)).isEmpty();
    }

    @Test
    void rejectsProposingAnUnlinkablePairUpFront() {
        Group business = groupService.create("Bad Business", soleOwnerId, Group.APPLET_ORDER_TRACKER);
        Group business2 = groupService.create("Bad Business 2", soleOwnerId, Group.APPLET_ORDER_TRACKER);

        assertThatThrownBy(() -> proposalService.propose(business.getId(), soleOwnerId, business2.getId(), List.of(), List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different applets");
    }

    @Test
    void aMemberLeavingMidApprovalInvalidatesTheProposal() {
        Group business = groupService.create("Invalidated Business", soleOwnerId, Group.APPLET_ORDER_TRACKER);
        business = groupService.addMember(business.getId(), memberAId);
        Group finance = groupService.create("Invalidated Finance", soleOwnerId, Group.APPLET_FINANCE_TRACKER);

        GroupLinkProposalView proposed = proposalService.propose(business.getId(), soleOwnerId, finance.getId(), List.of(), List.of());

        groupService.leave(business.getId(), memberAId);

        assertThat(proposalService.list(business.getId(), soleOwnerId))
                .anySatisfy(p -> assertThat(p.status()).isEqualTo(ApprovalStatus.INVALIDATED));
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(soleOwnerId))
                .anySatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.GROUP_LINK_INVALIDATED));
    }
}
