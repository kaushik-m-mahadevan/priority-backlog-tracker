package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
import com.backlogtracker.materialinventory.needle.domain.NeedleKind;
import com.backlogtracker.materialinventory.needle.dto.CreateNeedleTypeRequest;
import com.backlogtracker.materialinventory.needle.dto.NeedleInventoryEntryView;
import com.backlogtracker.materialinventory.needle.dto.NeedleTypeView;
import com.backlogtracker.materialinventory.needle.repository.NeedleInventoryEntryRepository;
import com.backlogtracker.materialinventory.needle.repository.NeedleTypeRepository;
import com.backlogtracker.materialinventory.needle.service.NeedleInventoryService;
import com.backlogtracker.materialinventory.needle.service.NeedleTypeService;

@SpringBootTest
class NeedleInventoryServiceTest {

    @Autowired GroupService groupService;
    @Autowired NeedleTypeService needleTypeService;
    @Autowired NeedleInventoryService needleInventoryService;
    @Autowired NeedleTypeRepository needleTypes;
    @Autowired NeedleInventoryEntryRepository entries;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String userAId;
    private String userBId;
    private Group inventoryGroup;

    @BeforeEach
    void setUp() {
        userAId = users.save(User.builder().name("Needle A").email("needle-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("needlea").build()).getId();
        userBId = users.save(User.builder().name("Needle B").email("needle-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("needleb").build()).getId();

        inventoryGroup = groupService.create("Needle Test Inventory", userAId, Group.APPLET_MATERIAL_INVENTORY);
        inventoryGroup = groupService.addMember(inventoryGroup.getId(), userBId);
    }

    @AfterEach
    void cleanUp() {
        entries.findByGroupId(inventoryGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        needleTypes.findByGroupId(inventoryGroup.getId()).forEach(n -> needleTypes.deleteById(n.getId()));
        groups.deleteById(inventoryGroup.getId());
        users.deleteById(userAId);
        users.deleteById(userBId);
    }

    private NeedleTypeView createHook() {
        return needleTypeService.create(inventoryGroup.getId(), userAId,
                new CreateNeedleTypeRequest(NeedleKind.CROCHET_HOOK, "4mm / US H-8", null));
    }

    // create+edit round trip, duplicate-combination rejection, cannot-delete-when-in-use,
    // and delete-when-unused are covered generically for both Yarn and Needle types in
    // TypeCrudContractTest (to-3) -- kept here only what's genuinely needle-specific below.

    @Test
    void aCrochetHookAndAKnittingNeedleOfTheSameSizeAreDifferentTypes() {
        createHook();

        NeedleTypeView knittingNeedle = needleTypeService.create(inventoryGroup.getId(), userAId,
                new CreateNeedleTypeRequest(NeedleKind.KNITTING_NEEDLE, "4mm / US H-8", null));

        assertThat(needleTypeService.list(inventoryGroup.getId(), userAId)).hasSize(2);
        assertThat(knittingNeedle.kind()).isEqualTo(NeedleKind.KNITTING_NEEDLE);
    }

    @Test
    void eachPersonSetsTheirOwnWholeUnitCountAndEveryoneCanSeeEveryonesInventory() {
        NeedleTypeView hook = createHook();

        needleInventoryService.setMyQuantity(inventoryGroup.getId(), userAId, hook.id(), 3);
        needleInventoryService.setMyQuantity(inventoryGroup.getId(), userBId, hook.id(), 1);

        List<NeedleInventoryEntryView> all = needleInventoryService.listAll(inventoryGroup.getId(), userAId);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(NeedleInventoryEntryView::userId, NeedleInventoryEntryView::quantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(userAId, 3),
                        org.assertj.core.groups.Tuple.tuple(userBId, 1));
    }

    @Test
    void settingQuantityToZeroRemovesTheEntry() {
        NeedleTypeView hook = createHook();
        needleInventoryService.setMyQuantity(inventoryGroup.getId(), userAId, hook.id(), 2);
        assertThat(needleInventoryService.mine(inventoryGroup.getId(), userAId)).hasSize(1);

        needleInventoryService.setMyQuantity(inventoryGroup.getId(), userAId, hook.id(), 0);

        assertThat(needleInventoryService.mine(inventoryGroup.getId(), userAId)).isEmpty();
    }

    @Test
    void rejectsANegativeQuantity() {
        NeedleTypeView hook = createHook();

        assertThatThrownBy(() -> needleInventoryService.setMyQuantity(inventoryGroup.getId(), userAId, hook.id(), -1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("negative");
    }
}
