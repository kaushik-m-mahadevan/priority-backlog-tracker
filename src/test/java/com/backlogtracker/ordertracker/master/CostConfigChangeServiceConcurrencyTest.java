package com.backlogtracker.ordertracker.master;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeRequest;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeStatus;
import com.backlogtracker.ordertracker.master.repository.CostConfigChangeRequestRepository;
import com.backlogtracker.ordertracker.master.service.CostConfigChangeService;

/** tc-3: real concurrent coverage for CostConfigChangeService.approve's retry loop, mirroring
 *  ApprovalServiceTest's equivalent race for the generic ApprovalService this class predates
 *  (kept as its own implementation per the platform's no-cross-applet-imports rule) --
 *  previously this OptimisticLockingFailureException retry path was only ever exercised
 *  sequentially. */
@SpringBootTest
class CostConfigChangeServiceConcurrencyTest {

    @Autowired GroupService groupService;
    @Autowired CostConfigChangeService changeService;
    @Autowired GroupRepository groups;
    @Autowired CostConfigChangeRequestRepository changeRequests;
    @Autowired UserRepository users;

    private String proposerId;

    @BeforeEach
    void setUp() {
        User proposer = users.save(User.builder().name("CCC Concurrency Proposer")
                .email("ccc-concurrency-proposer@x.test").passwordHash("x").role(Role.USER)
                .status(AccountStatus.ACTIVE).handle("cccconcurrencyproposer").build());
        proposerId = proposer.getId();
    }

    @AfterEach
    void cleanUp() {
        groups.findByMemberIdsContaining(proposerId).forEach(g -> {
            changeRequests.findByGroupIdAndStatus(g.getId(), CostConfigChangeStatus.PENDING)
                    .ifPresent(changeRequests::delete);
            g.getMemberIds().forEach(memberId -> {
                if (!memberId.equals(proposerId)) {
                    users.deleteById(memberId);
                }
            });
            groups.delete(g);
        });
        users.deleteById(proposerId);
    }

    @Test
    void concurrentApprovalsFromEveryMemberAllSucceedAndResolveExactlyOnce() throws Exception {
        Group group = groupService.create("CCC Concurrency Co", proposerId, Group.APPLET_ORDER_TRACKER);
        List<String> racerIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            User member = users.save(User.builder().name("CCC Racer " + i)
                    .email("ccc-concurrency-racer-" + i + "@x.test").passwordHash("x").role(Role.USER)
                    .status(AccountStatus.ACTIVE).handle("cccconcurrencyracer" + i).build());
            racerIds.add(member.getId());
            groupService.addMember(group.getId(), member.getId());
        }

        CostConfigChangeRequest proposed = changeService.propose(group.getId(), proposerId, 0.15, 0.20, 100);
        assertThat(proposed.getStatus()).isEqualTo(CostConfigChangeStatus.PENDING);

        ExecutorService pool = Executors.newFixedThreadPool(racerIds.size());
        CountDownLatch ready = new CountDownLatch(racerIds.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<CostConfigChangeRequest>> futures = racerIds.stream()
                    .map(memberId -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return changeService.approve(group.getId(), memberId, proposed.getId());
                    }))
                    .collect(Collectors.toList());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            for (var f : futures) {
                f.get(10, TimeUnit.SECONDS); // no exception == no permanently-lost retry race
            }

            CostConfigChangeRequest finalState = changeRequests.findById(proposed.getId()).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(CostConfigChangeStatus.APPROVED);
            List<String> expectedApprovers = new ArrayList<>(racerIds);
            expectedApprovers.add(proposerId);
            assertThat(finalState.getApprovedByUserIds()).containsExactlyInAnyOrderElementsOf(expectedApprovers);
        } finally {
            pool.shutdownNow();
        }
    }
}
