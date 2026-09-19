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
import com.backlogtracker.materialinventory.inventory.repository.InventoryEntryRepository;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.transfer.domain.TransferStatus;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.TransferRequestView;
import com.backlogtracker.materialinventory.transfer.repository.TransferRequestRepository;
import com.backlogtracker.materialinventory.transfer.service.TransferRequestService;
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.dto.YarnTypeView;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

@SpringBootTest
class TransferRequestServiceTest {

    @Autowired GroupService groupService;
    @Autowired YarnTypeService yarnTypeService;
    @Autowired InventoryService inventoryService;
    @Autowired TransferRequestService transferRequestService;
    @Autowired YarnTypeRepository yarnTypes;
    @Autowired InventoryEntryRepository entries;
    @Autowired TransferRequestRepository transfers;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String requesterId;
    private String targetId;
    private Group inventoryGroup;
    private YarnTypeView wool;

    @BeforeEach
    void setUp() {
        requesterId = users.save(User.builder().name("Requester").email("transfer-req@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("transferreq").build()).getId();
        targetId = users.save(User.builder().name("Target").email("transfer-target@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("transfertarget").build()).getId();

        inventoryGroup = groupService.create("Transfer Test Inventory", requesterId, Group.APPLET_MATERIAL_INVENTORY);
        inventoryGroup = groupService.addMember(inventoryGroup.getId(), targetId);

        wool = yarnTypeService.create(inventoryGroup.getId(), requesterId,
                new CreateYarnTypeRequest("Lion Brand", "Bulky (5)", "Ocean Blue", null, null, null, null, null, null));
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 3.0);
    }

    @AfterEach
    void cleanUp() {
        transfers.findByGroupId(inventoryGroup.getId()).forEach(t -> transfers.deleteById(t.getId()));
        entries.findByGroupId(inventoryGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        yarnTypes.findByGroupId(inventoryGroup.getId()).forEach(y -> yarnTypes.deleteById(y.getId()));
        groups.deleteById(inventoryGroup.getId());
        users.deleteById(requesterId);
        users.deleteById(targetId);
    }

    @Test
    void requesterCreatesATargetedRequestFromASpecificMember() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 1.5));

        assertThat(created.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(created.requesterId()).isEqualTo(requesterId);
        assertThat(created.targetUserId()).isEqualTo(targetId);
    }

    @Test
    void onlyTheTargetCanFulfillAndPartialFulfillmentMovesQuantityBetweenBothRows() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 2.0));

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), requesterId, created.id(), 1.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the person holding");

        TransferRequestView afterFirst = transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 1.0);
        assertThat(afterFirst.status()).isEqualTo(TransferStatus.PARTIALLY_FULFILLED);
        assertThat(afterFirst.fulfilledQuantity()).isEqualTo(1.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), targetId).get(0).quantity()).isEqualTo(2.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), requesterId).get(0).quantity()).isEqualTo(1.0);

        TransferRequestView afterSecond = transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 1.0);
        assertThat(afterSecond.fulfilledQuantity()).isEqualTo(2.0);
        // still not auto-completed — only the target explicitly marking it complete does that
        assertThat(afterSecond.status()).isEqualTo(TransferStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void rejectsFulfillingMoreThanIsStillRequested() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 1.0));

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 2.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than is still requested");
    }

    /** to-5: strengthening this assertion surfaced a real gap -- the target's on-hand
     *  quantity was never actually set below the fulfillment amount, so the test was
     *  silently exercising the SAME "more than is still requested" cap as the test above
     *  rather than InventoryService.adjustQuantity's own "not enough on hand" rejection.
     *  Fixed by giving the target genuinely insufficient stock relative to what's requested. */
    @Test
    void rejectsFulfillingMoreThanTheTargetHasOnHand() {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 2.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 3.0));

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 3.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough on hand");
    }

    /** Regression coverage for unreserve()'s compensating rollback — the one behavior the
     *  class's own javadoc specifically calls out, with zero prior coverage at any level.
     *  Reservation only checks the request's own bookkeeping (amount <= what's still
     *  requested), not real on-hand inventory — so a fulfill() whose reservation succeeds
     *  but whose physical withdrawal then fails (the target's actual stock changed in the
     *  interim) must roll fulfilledQuantity back to what it was before, not leave it
     *  claiming yarn that was never actually handed over. */
    @Test
    void unreservesOnAFailedInventoryWithdrawalAfterAValidReservation() {
        // Requested quantity (3.0) is within bounds for reservation, but the target's real
        // on-hand stock (1.0) is not enough to actually fulfill it.
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 1.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 3.0));

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 3.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough on hand");

        TransferRequestView afterFailedFulfill = transferRequestService.list(inventoryGroup.getId(), requesterId).stream()
                .filter(t -> t.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(afterFailedFulfill.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(afterFailedFulfill.fulfilledQuantity()).isEqualTo(0.0);
        // The target's own stock is untouched too — the withdrawal itself never applied.
        assertThat(inventoryService.mine(inventoryGroup.getId(), targetId).get(0).quantity()).isEqualTo(1.0);

        // The rollback actually worked, not just "didn't crash": a subsequent fulfillment
        // within the target's real means still succeeds against the same request.
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 3.0);
        TransferRequestView afterRetry = transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 3.0);
        assertThat(afterRetry.fulfilledQuantity()).isEqualTo(3.0);
    }

    @Test
    void onlyTheTargetCanMarkCompleteEvenBeforeFullyFulfilled() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 2.0));
        transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 0.5);

        assertThatThrownBy(() -> transferRequestService.markComplete(inventoryGroup.getId(), requesterId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the person holding");

        TransferRequestView completed = transferRequestService.markComplete(inventoryGroup.getId(), targetId, created.id());
        assertThat(completed.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(completed.fulfilledQuantity()).isEqualTo(0.5);
    }

    @Test
    void onlyTheRequesterCanCancelTheirOwnRequest() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 1.0));

        assertThatThrownBy(() -> transferRequestService.cancel(inventoryGroup.getId(), targetId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the requester");

        TransferRequestView cancelled = transferRequestService.cancel(inventoryGroup.getId(), requesterId, created.id());
        assertThat(cancelled.status()).isEqualTo(TransferStatus.CANCELLED);
    }

    @Test
    void cannotActOnAnAlreadyResolvedRequest() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 1.0));
        transferRequestService.cancel(inventoryGroup.getId(), requesterId, created.id());

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 0.5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already CANCELLED");
    }

    /** Regression test for a lost-update race: fulfilledQuantity used to be a plain
     *  read-modify-write, so two concurrent partial fulfillments of the same request could
     *  both read the same starting fulfilledQuantity and one save could silently overwrite
     *  the other, undercounting how much was actually handed over. It's now a single atomic
     *  conditional increment (see {@code reserveFulfillment}), so N concurrent 0.25-skein
     *  fulfillments, all comfortably within the requested budget, must all be recorded —
     *  fulfilledQuantity lands on exactly their sum, never short. */
    @Test
    void concurrentPartialFulfillmentsAreAllRecordedNotLost() throws Exception {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 10.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 10.0));

        int n = 20;
        runSimultaneously(n, i -> {
            transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 0.25);
            return null;
        });

        TransferRequestView finalState = transferRequestService.list(inventoryGroup.getId(), requesterId).stream()
                .filter(t -> t.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(finalState.fulfilledQuantity()).isEqualTo(n * 0.25);
        assertThat(inventoryService.mine(inventoryGroup.getId(), requesterId).get(0).quantity()).isEqualTo(n * 0.25);
    }

    /** Regression test for the round 4 review's flagged residual risk: fulfill()'s
     *  "no more than requested" check used to read `remaining` once, before any retry
     *  protection, so a stale read could in principle let concurrent fulfillments push
     *  fulfilledQuantity past requestedQuantity. The cap is now enforced by the same atomic
     *  findAndModify that increments fulfilledQuantity (see reserveFulfillment), so of 20
     *  concurrent 1.0-skein fulfillment attempts against a 10.0-skein request, exactly 10
     *  must succeed and 10 must be rejected — fulfilledQuantity can never land above 10.0,
     *  under any interleaving. */
    @Test
    void concurrentFulfillmentsCanNeverPushFulfilledQuantityPastWhatWasRequested() throws Exception {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 20.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 10.0));

        int n = 20;
        List<Boolean> results = runSimultaneously(n, i -> {
            try {
                transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 1.0);
                return true;
            } catch (ResponseStatusException e) {
                return false;
            }
        });
        long succeeded = results.stream().filter(Boolean::booleanValue).count();
        assertThat(succeeded).isEqualTo(10);

        TransferRequestView finalState = transferRequestService.list(inventoryGroup.getId(), requesterId).stream()
                .filter(t -> t.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(finalState.fulfilledQuantity()).isEqualTo(10.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), requesterId).get(0).quantity()).isEqualTo(10.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), targetId).get(0).quantity()).isEqualTo(10.0);
    }

    /** Shared latch-gated "release all n threads at once" harness for the two concurrency
     *  tests above — previously each hand-rolled its own identical pool/latch bookkeeping. */
    private <T> List<T> runSimultaneously(int n, IntFunctionThrows<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n); // must fit every task at once for the latch below
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
