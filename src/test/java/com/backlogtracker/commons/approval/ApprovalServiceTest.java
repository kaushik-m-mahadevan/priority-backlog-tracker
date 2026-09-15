package com.backlogtracker.commons.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;
import com.backlogtracker.commons.approval.repository.ApprovalRequestRepository;
import com.backlogtracker.commons.approval.service.ApprovalService;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

@SpringBootTest
class ApprovalServiceTest {

    private static final String KIND = "test:widget-change";

    @Autowired GroupService groupService;
    @Autowired ApprovalService approvalService;
    @Autowired GroupRepository groups;
    @Autowired ApprovalRequestRepository requests;
    @Autowired UserRepository users;

    private String userId;
    private String otherId;

    @BeforeEach
    void setUp() {
        User u = users.save(User.builder().name("Approval Tester").email("approval-tester@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("approvaltester").build());
        userId = u.getId();
        User other = users.save(User.builder().name("Approval Other").email("approval-other@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("approvalother").build());
        otherId = other.getId();
    }

    @AfterEach
    void cleanUp() {
        groups.findByMemberIdsContaining(userId).forEach(g -> groups.delete(g));
        groups.findByMemberIdsContaining(otherId).forEach(g -> groups.delete(g));
        requests.findAll().forEach(r -> {
            if (KIND.equals(r.getKind())) {
                requests.delete(r);
            }
        });
        users.deleteById(userId);
        users.deleteById(otherId);
    }

    @Test
    void soloProposerInASingleMemberGroupAutoResolvesImmediately() {
        Group group = groupService.create("Solo", userId, Group.APPLET_ORDER_TRACKER);

        ApprovalRequest request = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 42));

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(request.getApprovedByUserIds()).containsExactly(userId);
    }

    @Test
    void requiresEveryCurrentMemberToApproveBeforeResolving() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);

        ApprovalRequest proposed = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 7));
        assertThat(proposed.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        ApprovalRequest resolved = approvalService.approve(group.getId(), otherId, proposed.getId());

        assertThat(resolved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(resolved.getApprovedByUserIds()).containsExactlyInAnyOrder(userId, otherId);
    }

    @Test
    void aSingleRejectionCancelsTheWholeProposal() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);

        ApprovalRequest proposed = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 7));
        ApprovalRequest rejected = approvalService.reject(group.getId(), otherId, proposed.getId());

        assertThat(rejected.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(rejected.getRejectedByUserId()).isEqualTo(otherId);
    }

    @Test
    void onlyOnePendingRequestPerKindIsAllowedAtATimePerGroup() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);
        approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));

        assertThatThrownBy(() -> approvalService.propose(group.getId(), userId, KIND, Map.of("value", 2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void differentKindsInTheSameGroupDoNotBlockEachOther() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);
        approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));

        ApprovalRequest other = approvalService.propose(group.getId(), userId, KIND + ":other", Map.of("value", 2));

        assertThat(other.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        requests.delete(other);
    }

    @Test
    void aMemberLeavingMidApprovalInvalidatesThePendingRequest() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);
        ApprovalRequest proposed = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));

        groupService.leave(group.getId(), otherId);

        ApprovalRequest reloaded = requests.findById(proposed.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApprovalStatus.INVALIDATED);
    }

    @Test
    void afterInvalidationTheProposerCanProposeAgain() {
        Group group = groupService.create("Duo", userId, Group.APPLET_ORDER_TRACKER);
        groupService.addMember(group.getId(), otherId);
        approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));
        groupService.leave(group.getId(), otherId);

        ApprovalRequest reproposed = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 2));

        assertThat(reproposed.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void approvingOrRejectingAnAlreadyResolvedRequestIsRejected() {
        Group group = groupService.create("Solo", userId, Group.APPLET_ORDER_TRACKER);
        ApprovalRequest resolved = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));

        assertThatThrownBy(() -> approvalService.approve(group.getId(), userId, resolved.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been resolved");
    }
}
