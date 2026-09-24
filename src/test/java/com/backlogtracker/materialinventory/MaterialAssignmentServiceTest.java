package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignmentStatus;
import com.backlogtracker.materialinventory.assignment.dto.CreateMaterialAssignmentRequest;
import com.backlogtracker.materialinventory.assignment.dto.MaterialAssignmentView;
import com.backlogtracker.materialinventory.assignment.repository.MaterialAssignmentRepository;
import com.backlogtracker.materialinventory.assignment.service.MaterialAssignmentService;
import com.backlogtracker.materialinventory.inventory.repository.InventoryEntryRepository;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.dto.YarnTypeView;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

/** mb-21: propose/accept yarn hand-off between two named members, chosen over a direct set
 *  specifically for the audit trail. */
@SpringBootTest
class MaterialAssignmentServiceTest {

    @Autowired GroupService groupService;
    @Autowired YarnTypeService yarnTypeService;
    @Autowired InventoryService inventoryService;
    @Autowired MaterialAssignmentService assignmentService;
    @Autowired YarnTypeRepository yarnTypes;
    @Autowired InventoryEntryRepository entries;
    @Autowired MaterialAssignmentRepository assignments;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String proposerId;
    private String recipientId;
    private Group inventoryGroup;
    private YarnTypeView wool;

    @BeforeEach
    void setUp() {
        proposerId = users.save(User.builder().name("Proposer").email("assign-proposer@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("assignproposer").build()).getId();
        recipientId = users.save(User.builder().name("Recipient").email("assign-recipient@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("assignrecipient").build()).getId();

        inventoryGroup = groupService.create("Assignment Test Inventory", proposerId, Group.APPLET_MATERIAL_INVENTORY);
        inventoryGroup = groupService.addMember(inventoryGroup.getId(), recipientId);

        wool = yarnTypeService.create(inventoryGroup.getId(), proposerId,
                new CreateYarnTypeRequest("Lion Brand", "Bulky (5)", "Ocean Blue", null, null, null, null, null, null));
        inventoryService.setMyQuantity(inventoryGroup.getId(), proposerId, wool.id(), 10.0);
    }

    @AfterEach
    void cleanUp() {
        assignments.findByGroupId(inventoryGroup.getId()).forEach(a -> assignments.deleteById(a.getId()));
        entries.findByGroupId(inventoryGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        yarnTypes.findByGroupId(inventoryGroup.getId()).forEach(y -> yarnTypes.deleteById(y.getId()));
        groups.deleteById(inventoryGroup.getId());
        users.deleteById(proposerId);
        users.deleteById(recipientId);
    }

    @Test
    void proposingDebitsTheProposerImmediatelyAndCreditsNoOneYet() {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 5.0));

        assertThat(proposed.status()).isEqualTo(MaterialAssignmentStatus.PENDING);
        assertThat(inventoryService.mine(inventoryGroup.getId(), proposerId).get(0).quantity()).isEqualTo(5.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), recipientId)).isEmpty();
    }

    @Test
    void rejectsProposingMoreThanTheProposerHasOnHand() {
        assertThatThrownBy(() -> assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 20.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough on hand");
        // nothing was debited by the failed attempt
        assertThat(inventoryService.mine(inventoryGroup.getId(), proposerId).get(0).quantity()).isEqualTo(10.0);
    }

    @Test
    void onlyTheRecipientCanAcceptAndAcceptingLandsItOnTheirOwnRow() {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 5.0));

        assertThatThrownBy(() -> assignmentService.accept(inventoryGroup.getId(), proposerId, proposed.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the recipient");

        MaterialAssignmentView accepted = assignmentService.accept(inventoryGroup.getId(), recipientId, proposed.id());

        assertThat(accepted.status()).isEqualTo(MaterialAssignmentStatus.ACCEPTED);
        assertThat(inventoryService.mine(inventoryGroup.getId(), recipientId).get(0).quantity()).isEqualTo(5.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), proposerId).get(0).quantity()).isEqualTo(5.0);
    }

    @Test
    void rejectingRefundsTheProposerInFull() {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 5.0));

        MaterialAssignmentView rejected = assignmentService.reject(inventoryGroup.getId(), recipientId, proposed.id());

        assertThat(rejected.status()).isEqualTo(MaterialAssignmentStatus.REJECTED);
        assertThat(inventoryService.mine(inventoryGroup.getId(), proposerId).get(0).quantity()).isEqualTo(10.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), recipientId)).isEmpty();
    }

    @Test
    void onlyTheProposerCanCancelAndCancellingRefundsThem() {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 4.0));

        assertThatThrownBy(() -> assignmentService.cancel(inventoryGroup.getId(), recipientId, proposed.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the proposer");

        MaterialAssignmentView cancelled = assignmentService.cancel(inventoryGroup.getId(), proposerId, proposed.id());
        assertThat(cancelled.status()).isEqualTo(MaterialAssignmentStatus.CANCELLED);
        assertThat(inventoryService.mine(inventoryGroup.getId(), proposerId).get(0).quantity()).isEqualTo(10.0);
    }

    @Test
    void cannotActOnAnAlreadyResolvedAssignment() {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 3.0));
        assignmentService.cancel(inventoryGroup.getId(), proposerId, proposed.id());

        assertThatThrownBy(() -> assignmentService.accept(inventoryGroup.getId(), recipientId, proposed.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already CANCELLED");
    }

    @Test
    void rejectsSelfAssignment() {
        assertThatThrownBy(() -> assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(proposerId, wool.id(), 1.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("yourself");
    }

    /** Regression coverage for the atomic PENDING-guard: two concurrent accept attempts on
     *  the very same assignment must never both succeed — only one may land the credit on
     *  the recipient's row, exactly once. */
    @Test
    void concurrentAcceptAttemptsOnTheSameAssignmentOnlyEverSucceedOnce() throws Exception {
        MaterialAssignmentView proposed = assignmentService.propose(inventoryGroup.getId(), proposerId,
                new CreateMaterialAssignmentRequest(recipientId, wool.id(), 5.0));

        int n = 10;
        List<Boolean> results = runSimultaneously(n, i -> {
            try {
                assignmentService.accept(inventoryGroup.getId(), recipientId, proposed.id());
                return true;
            } catch (ResponseStatusException e) {
                return false;
            }
        });

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(inventoryService.mine(inventoryGroup.getId(), recipientId).get(0).quantity()).isEqualTo(5.0);
    }

    private <T> List<T> runSimultaneously(int n, IntFunctionThrows<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<T>> tasks = IntStream.range(0, n)
                    .<Callable<T>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        return task.apply(i);
                    })
                    .toList();
            List<java.util.concurrent.Future<T>> futures = tasks.stream().map(pool::submit).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (var f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IntFunctionThrows<T> {
        T apply(int i) throws Exception;
    }
}
