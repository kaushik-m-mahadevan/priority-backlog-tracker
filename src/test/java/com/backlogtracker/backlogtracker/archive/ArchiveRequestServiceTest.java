package com.backlogtracker.backlogtracker.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest;
import com.backlogtracker.backlogtracker.archive.repository.ArchiveRequestRepository;
import com.backlogtracker.backlogtracker.archive.service.ArchiveRequestService;
import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

/** Concurrency regression tests for the archive-request approval race fixed in round 4 —
 *  see ArchiveRequestApiTest for the plain-path API-level behavior. */
@SpringBootTest
class ArchiveRequestServiceTest {

    @Autowired GroupService groupService;
    @Autowired ArchiveRequestService archiveRequestService;
    @Autowired ItemRepository items;
    @Autowired ArchiveRequestRepository requests;
    @Autowired UserRepository users;

    private Group group;
    private List<AuthUser> members;
    private Item item;

    @BeforeEach
    void setUp() {
        List<User> saved = IntStream.range(0, 6)
                .mapToObj(i -> users.save(User.builder().name("Voter " + i).email("voter" + i + "@x.test")
                        .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("voter" + i).build()))
                .toList();
        members = saved.stream().map(AuthUser::from).toList();

        group = groupService.create("Archive Race Group", members.get(0).id(), Group.APPLET_BACKLOG_TRACKER);
        for (int i = 1; i < members.size(); i++) {
            group = groupService.addMember(group.getId(), members.get(i).id());
        }

        item = items.save(Item.builder()
                .groupId(group.getId())
                .itemId("ITM-RACE-001")
                .title("Race target")
                .category("Project")
                .priority("Medium")
                .effortEstimate(new EffortEstimate(1, EffortUnit.HOURS))
                .status(ItemStatus.BACKLOG)
                .createdBy(members.get(0).id())
                .lastUpdatedBy(members.get(0).id())
                .build());
    }

    @AfterEach
    void cleanUp() {
        requests.findByGroupIdAndStatus(group.getId(), ArchiveRequest.Status.PENDING).forEach(r -> requests.deleteById(r.getId()));
        requests.findByGroupIdAndStatus(group.getId(), ArchiveRequest.Status.APPROVED).forEach(r -> requests.deleteById(r.getId()));
        items.findById(item.getId()).ifPresent(i -> items.deleteById(i.getId()));
        groupService.leave(group.getId(), members.get(0).id());
        for (int i = 1; i < members.size(); i++) {
            users.deleteById(members.get(i).id());
        }
    }

    /** Regression test for the lost-update race: approve() used to be a plain
     *  read-modify-write on approvedByUserIds with no @Version, so concurrent approvals
     *  could silently overwrite one another. All 5 non-requesting members approving at
     *  once must all be recorded — the request must reach APPROVED, not get stuck PENDING
     *  with a lost vote. */
    @Test
    void concurrentApprovalsFromEveryMemberAreAllRecordedAndReachUnanimity() throws Exception {
        ArchiveRequest req = archiveRequestFor(item.getId(), members.get(0));

        List<AuthUser> approvers = members.subList(1, members.size());
        ExecutorService pool = Executors.newFixedThreadPool(approvers.size());
        try {
            List<Callable<Void>> tasks = approvers.stream()
                    .<Callable<Void>>map(actor -> () -> {
                        archiveRequestService.approve(req.getId(), actor);
                        return null;
                    })
                    .toList();
            for (var f : pool.invokeAll(tasks)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        ArchiveRequest resolved = requests.findById(req.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ArchiveRequest.Status.APPROVED);
        assertThat(resolved.getApprovedByUserIds()).containsExactlyInAnyOrderElementsOf(
                members.stream().map(AuthUser::id).toList());
        // and the item was actually archived exactly once
        assertThat(items.findById(item.getId())).isEmpty();
    }

    private ArchiveRequest archiveRequestFor(String itemId, AuthUser requester) {
        var view = archiveRequestService.create(itemId, requester, null);
        return requests.findById(view.getId()).orElseThrow();
    }
}
