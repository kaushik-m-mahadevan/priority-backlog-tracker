package com.backlogtracker.commons.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    /** Real concurrent coverage for approve()'s retry loop: previously only ever called
     *  sequentially in tests, so the OptimisticLockingFailureException retry path (two
     *  members racing to append their own id to the same approvedByUserIds list) was never
     *  actually exercised under real contention. */
    @Test
    void concurrentApprovalsFromEveryMemberAllSucceedAndResolveExactlyOnce() throws Exception {
        Group group = groupService.create("Crowd", userId, Group.APPLET_ORDER_TRACKER);
        List<User> extraMembers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            User member = users.save(User.builder().name("Approval Racer " + i)
                    .email("approval-racer-" + i + "@x.test").passwordHash("x").role(Role.USER)
                    .status(AccountStatus.ACTIVE).handle("approvalracer" + i).build());
            extraMembers.add(member);
            groupService.addMember(group.getId(), member.getId());
        }
        groupService.addMember(group.getId(), otherId);

        ApprovalRequest proposed = approvalService.propose(group.getId(), userId, KIND, Map.of("value", 1));
        List<String> racers = extraMembers.stream().map(User::getId).collect(Collectors.toList());
        racers.add(otherId);

        ExecutorService pool = Executors.newFixedThreadPool(racers.size());
        CountDownLatch ready = new CountDownLatch(racers.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<ApprovalRequest>> futures = racers.stream()
                    .map(memberId -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return approvalService.approve(group.getId(), memberId, proposed.getId());
                    }))
                    .collect(Collectors.toList());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<ApprovalRequest> results = new ArrayList<>();
            for (var f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }

            ApprovalRequest finalState = requests.findById(proposed.getId()).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            List<String> expectedApprovers = new ArrayList<>(racers);
            expectedApprovers.add(userId);
            assertThat(finalState.getApprovedByUserIds()).containsExactlyInAnyOrderElementsOf(expectedApprovers);
            // every racing call must have completed without throwing (no lost update ever
            // surfaces as a permanent OptimisticLockingFailureException after MAX_RETRIES)
            assertThat(results).hasSize(racers.size());
        } finally {
            pool.shutdownNow();
            extraMembers.forEach(m -> users.deleteById(m.getId()));
        }
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
