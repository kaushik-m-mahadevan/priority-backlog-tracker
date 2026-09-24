package com.backlogtracker.commons.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.link.repository.GroupLinkRepository;
import com.backlogtracker.commons.link.service.GroupLinkService;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.repository.NotificationRepository;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

@SpringBootTest
class GroupLinkServiceTest {

    @Autowired GroupService groupService;
    @Autowired GroupLinkService linkService;
    @Autowired GroupRepository groups;
    @Autowired GroupLinkRepository links;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    private String userId;
    private String outsiderId;
    private String secondMemberId;

    @BeforeEach
    void setUp() {
        User u = users.save(User.builder().name("Link Tester").email("link-tester@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linktester").build());
        userId = u.getId();
        User outsider = users.save(User.builder().name("Outsider").email("link-outsider@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linkoutsider").build());
        outsiderId = outsider.getId();
        User second = users.save(User.builder().name("Second Member").email("link-second@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("linksecond").build());
        secondMemberId = second.getId();
    }

    @AfterEach
    void cleanUp() {
        groups.findByMemberIdsContaining(userId).forEach(g -> groups.delete(g));
        groups.findByMemberIdsContaining(outsiderId).forEach(g -> groups.delete(g));
        groups.findByMemberIdsContaining(secondMemberId).forEach(g -> groups.delete(g));
        users.deleteById(userId);
        users.deleteById(outsiderId);
        users.deleteById(secondMemberId);
    }

    /** mb-23: the Setup Wizard's "invite all outright" behaviour — every business member
     *  missing from the newly-linked group gets a real invite, with no suggestion/
     *  confirmation step (unlike the manual link path, which defaults inviteAllMembers to
     *  false and leaves the delta review to the linker). */
    @Test
    void inviteAllMembersInvitesEveryBusinessMemberMissingFromTheNewlyLinkedGroup() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        business = groupService.addMember(business.getId(), secondMemberId);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);

        linkService.link(business.getId(), userId, finance.getId(), true);

        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(secondMemberId))
                .anySatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.GROUP_INVITE);
                    assertThat(n.getGroupId()).isEqualTo(finance.getId());
                });
        // the sole finance-group member (userId) is already in the business both ways —
        // nothing to invite back into the business, and no invite to itself either.
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(userId))
                .noneMatch(n -> n.getType() == NotificationType.GROUP_INVITE);
    }

    @Test
    void manualLinkWithoutInviteAllMembersSendsNoInvites() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        business = groupService.addMember(business.getId(), secondMemberId);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);

        linkService.link(business.getId(), userId, finance.getId());

        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(secondMemberId))
                .noneMatch(n -> n.getType() == NotificationType.GROUP_INVITE);
    }

    @Test
    void linksTwoGroupsFromDifferentAppletsAndCanBeReadBackFromEitherSide() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);

        linkService.link(business.getId(), userId, finance.getId());

        assertThat(linkService.linkedGroupId(business.getId(), userId, Group.APPLET_FINANCE_TRACKER))
                .contains(finance.getId());
        assertThat(linkService.linkedGroupId(finance.getId(), userId, Group.APPLET_ORDER_TRACKER))
                .contains(business.getId());
    }

    @Test
    void rejectsLinkingTwoGroupsOfTheSameApplet() {
        Group b1 = groupService.create("Business 1", userId, Group.APPLET_ORDER_TRACKER);
        Group b2 = groupService.create("Business 2", userId, Group.APPLET_ORDER_TRACKER);

        assertThatThrownBy(() -> linkService.link(b1.getId(), userId, b2.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different applets");
    }

    @Test
    void enforcesOneFinanceGroupPerBusiness() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance1 = groupService.create("Finance 1", userId, Group.APPLET_FINANCE_TRACKER);
        Group finance2 = groupService.create("Finance 2", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance1.getId());

        assertThatThrownBy(() -> linkService.link(business.getId(), userId, finance2.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void enforcesOneBusinessPerFinanceGroup() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group business2 = groupService.create("Business 2", userId, Group.APPLET_ORDER_TRACKER);
        Group finance1 = groupService.create("Finance 1", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance1.getId());

        assertThatThrownBy(() -> linkService.link(business2.getId(), userId, finance1.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already linked");
    }

    /** Real concurrent coverage for link()'s duplicate-key race: previously only ever
     *  called sequentially, so the DuplicateKeyException catch block -- the actual guard
     *  once two callers race past the findByGroupIdA.../findByGroupIdB... pre-checks --
     *  was never really exercised under contention. */
    @Test
    void concurrentLinkAttemptsOnTheSamePairLeaveExactlyOneLinkAndRejectTheRest() throws Exception {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);

        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = IntStream.range(0, racers)
                    .<Future<Object>>mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            return linkService.link(business.getId(), userId, finance.getId());
                        } catch (ResponseStatusException e) {
                            return e;
                        }
                    }))
                    .collect(Collectors.toList());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> results = new java.util.ArrayList<>();
            for (var f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }

            long succeeded = results.stream().filter(r -> r instanceof com.backlogtracker.commons.link.domain.GroupLink).count();
            long conflicted = results.stream().filter(r -> r instanceof ResponseStatusException e
                    && e.getStatusCode() == HttpStatus.CONFLICT).count();
            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicted).isEqualTo(racers - 1);
            assertThat(links.findByGroupIdAAndAppletKeyB(business.getId(), Group.APPLET_FINANCE_TRACKER)).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unlinkRemovesTheLinkFromEitherSide() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance.getId());

        linkService.unlink(finance.getId(), userId, Group.APPLET_ORDER_TRACKER);

        assertThat(linkService.linkedGroupId(business.getId(), userId, Group.APPLET_FINANCE_TRACKER)).isEmpty();
    }

    @Test
    void linkingRequiresMembershipInBothGroups() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", outsiderId, Group.APPLET_FINANCE_TRACKER);

        assertThatThrownBy(() -> linkService.link(business.getId(), userId, finance.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("not a member");
    }

    @Test
    void readingALinkRequiresMembershipInTheGroupAsked() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance.getId());

        assertThatThrownBy(() -> linkService.linkedGroupId(business.getId(), outsiderId, Group.APPLET_FINANCE_TRACKER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("not a member");
    }

    @Test
    void deletingAGroupCleansUpItsLinkSoThePairingCanBeUsedAgain() {
        Group business = groupService.create("Business", userId, Group.APPLET_ORDER_TRACKER);
        Group finance = groupService.create("Finance", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance.getId());

        groupService.leave(finance.getId(), userId); // last member — deletes the group

        assertThat(links.findByGroupIdAAndAppletKeyB(business.getId(), Group.APPLET_FINANCE_TRACKER)).isEmpty();

        // the pairing is free again — a new finance group can now be linked
        Group finance2 = groupService.create("Finance 2", userId, Group.APPLET_FINANCE_TRACKER);
        linkService.link(business.getId(), userId, finance2.getId());
        assertThat(linkService.linkedGroupId(business.getId(), userId, Group.APPLET_FINANCE_TRACKER))
                .contains(finance2.getId());
    }
}
