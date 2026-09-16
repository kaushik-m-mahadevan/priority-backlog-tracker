package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsFulfillingMoreThanTheTargetHasOnHand() {
        TransferRequestView created = transferRequestService.create(inventoryGroup.getId(), requesterId,
                new CreateTransferRequestRequest(targetId, wool.id(), 3.0));

        assertThatThrownBy(() -> transferRequestService.fulfill(inventoryGroup.getId(), targetId, created.id(), 3.0 + 1.0))
                .isInstanceOf(ResponseStatusException.class);
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
}
