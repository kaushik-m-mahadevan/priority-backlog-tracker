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
import com.backlogtracker.materialinventory.transfer.domain.LineStatus;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest;
import com.backlogtracker.materialinventory.transfer.dto.CreateTransferRequestRequest.LineInput;
import com.backlogtracker.materialinventory.transfer.dto.SendShipmentRequest;
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
    private YarnTypeView cotton;

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
        cotton = yarnTypeService.create(inventoryGroup.getId(), requesterId,
                new CreateYarnTypeRequest("Lily Sugar'n Cream", "Worsted (4)", "Ecru", null, null, null, null, null, null));
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

    private static SendShipmentRequest send(double quantity) {
        return new SendShipmentRequest(quantity, null);
    }

    private TransferRequestView.LineView onlyLine(TransferRequestView view) {
        return view.lines().get(0);
    }

    @Test
    void requesterCreatesATargetedRequestFromASpecificMember() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.5))));

        assertThat(created.requesterId()).isEqualTo(requesterId);
        assertThat(created.targetUserId()).isEqualTo(targetId);
        assertThat(onlyLine(created).status()).isEqualTo(LineStatus.OPEN);
        assertThat(onlyLine(created).requestedQuantity()).isEqualTo(1.5);
    }

    @Test
    void aRequestCanCoverMultipleYarnTypesInOneGo() {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, cotton.id(), 5.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0), new LineInput(cotton.id(), 2.0))));

        assertThat(created.lines()).hasSize(2);
        assertThat(created.lines()).extracting(TransferRequestView.LineView::yarnTypeId)
                .containsExactlyInAnyOrder(wool.id(), cotton.id());
    }

    @Test
    void rejectsTheSameYarnTypeTwiceInOneRequest() {
        assertThatThrownBy(() -> transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0), new LineInput(wool.id(), 1.0)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only appear once");
    }

    @Test
    void sendingDebitsTheTargetImmediatelyButOnlyConfirmingCreditsTheRequester() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 2.0))));
        String lineId = onlyLine(created).lineId();

        assertThatThrownBy(() -> transferRequestService.send(inventoryGroup.getId(), requesterId, created.id(), lineId, send(1.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the person holding");

        TransferRequestView afterSend = transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));
        assertThat(onlyLine(afterSend).sentQuantity()).isEqualTo(1.0);
        assertThat(onlyLine(afterSend).receivedQuantity()).isEqualTo(0.0);
        // The target's on-hand already dropped — the yarn is "in transit", credited to neither party.
        assertThat(inventoryService.mine(inventoryGroup.getId(), targetId).get(0).quantity()).isEqualTo(2.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), requesterId)).isEmpty();

        String shipmentId = onlyLine(afterSend).shipments().get(0).shipmentId();
        assertThatThrownBy(() -> transferRequestService.confirmReceived(inventoryGroup.getId(), targetId, created.id(), lineId, shipmentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the requester");

        TransferRequestView afterConfirm = transferRequestService.confirmReceived(inventoryGroup.getId(), requesterId, created.id(), lineId, shipmentId);
        assertThat(onlyLine(afterConfirm).receivedQuantity()).isEqualTo(1.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), requesterId).get(0).quantity()).isEqualTo(1.0);
    }

    @Test
    void aTargetCanSendInSeveralInstallmentsOverTime() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 2.0))));
        String lineId = onlyLine(created).lineId();

        transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));
        TransferRequestView afterSecond = transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));

        assertThat(onlyLine(afterSecond).sentQuantity()).isEqualTo(2.0);
        assertThat(onlyLine(afterSecond).shipments()).hasSize(2);
    }

    @Test
    void rejectsSendingMoreThanIsStillRequested() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0))));
        String lineId = onlyLine(created).lineId();

        assertThatThrownBy(() -> transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(2.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than is still requested");
    }

    @Test
    void rejectsSendingMoreThanTheTargetHasOnHand() {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 2.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 3.0))));
        String lineId = onlyLine(created).lineId();

        assertThatThrownBy(() -> transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(3.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough on hand");

        // A rejected send leaves nothing behind to roll back — the request is untouched.
        TransferRequestView unchanged = transferRequestService.list(inventoryGroup.getId(), requesterId).get(0);
        assertThat(onlyLine(unchanged).shipments()).isEmpty();
    }

    @Test
    void closingALineStopsFurtherSendsButNeverTouchesAShipmentAlreadySent() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 3.0))));
        String lineId = onlyLine(created).lineId();
        TransferRequestView afterSend = transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));
        String shipmentId = onlyLine(afterSend).shipments().get(0).shipmentId();

        assertThatThrownBy(() -> transferRequestService.closeLine(inventoryGroup.getId(), targetId, created.id(), lineId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the requester");

        TransferRequestView afterClose = transferRequestService.closeLine(inventoryGroup.getId(), requesterId, created.id(), lineId);
        assertThat(onlyLine(afterClose).status()).isEqualTo(LineStatus.CLOSED);

        assertThatThrownBy(() -> transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("closed");

        // The 1.0 already sent before closing is still receivable — closing never undoes it.
        TransferRequestView afterConfirm = transferRequestService.confirmReceived(inventoryGroup.getId(), requesterId, created.id(), lineId, shipmentId);
        assertThat(onlyLine(afterConfirm).receivedQuantity()).isEqualTo(1.0);
    }

    @Test
    void cancelOnlyWorksBeforeAnythingHasBeenSent() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0))));
        String lineId = onlyLine(created).lineId();

        assertThatThrownBy(() -> transferRequestService.cancel(inventoryGroup.getId(), targetId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the requester");

        transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(0.5));
        assertThatThrownBy(() -> transferRequestService.cancel(inventoryGroup.getId(), requesterId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("close individual lines instead");
    }

    @Test
    void cancelClosesEveryLineWhenNothingWasEverSent() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0))));

        TransferRequestView cancelled = transferRequestService.cancel(inventoryGroup.getId(), requesterId, created.id());
        assertThat(onlyLine(cancelled).status()).isEqualTo(LineStatus.CLOSED);
    }

    @Test
    void cannotConfirmTheSameShipmentTwice() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 1.0))));
        String lineId = onlyLine(created).lineId();
        TransferRequestView afterSend = transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));
        String shipmentId = onlyLine(afterSend).shipments().get(0).shipmentId();

        transferRequestService.confirmReceived(inventoryGroup.getId(), requesterId, created.id(), lineId, shipmentId);

        assertThatThrownBy(() -> transferRequestService.confirmReceived(inventoryGroup.getId(), requesterId, created.id(), lineId, shipmentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already confirmed");
    }

    /** Regression coverage for a lost-update race on the embedded document: two concurrent
     *  sends against the same line must never both succeed past what was actually requested
     *  — the {@code @Version}-guarded save rejects the loser of the race with a conflict
     *  instead of one silently overwriting the other's shipment. */
    @Test
    void concurrentSendsCanNeverPushSentQuantityPastWhatWasRequested() throws Exception {
        inventoryService.setMyQuantity(inventoryGroup.getId(), targetId, wool.id(), 20.0);
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, List.of(new LineInput(wool.id(), 10.0))));
        String lineId = onlyLine(created).lineId();

        int n = 20;
        List<Boolean> results = runSimultaneously(n, i -> {
            try {
                transferRequestService.send(inventoryGroup.getId(), targetId, created.id(), lineId, send(1.0));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });
        long succeeded = results.stream().filter(Boolean::booleanValue).count();
        assertThat(succeeded).isEqualTo(10);

        TransferRequestView finalState = transferRequestService.list(inventoryGroup.getId(), requesterId).stream()
                .filter(t -> t.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(onlyLine(finalState).sentQuantity()).isEqualTo(10.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), targetId).get(0).quantity()).isEqualTo(10.0);
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
