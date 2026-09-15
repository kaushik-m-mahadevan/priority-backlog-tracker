package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.backlogtracker.materialinventory.inventory.dto.InventoryEntryView;
import com.backlogtracker.materialinventory.inventory.repository.InventoryEntryRepository;
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.dto.YarnTypeView;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

@SpringBootTest
class MaterialInventoryServiceTest {

    @Autowired GroupService groupService;
    @Autowired YarnTypeService yarnTypeService;
    @Autowired InventoryService inventoryService;
    @Autowired YarnTypeRepository yarnTypes;
    @Autowired InventoryEntryRepository entries;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String userAId;
    private String userBId;
    private Group inventoryGroup;

    @BeforeEach
    void setUp() {
        userAId = users.save(User.builder().name("Yarn A").email("yarn-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("yarna").build()).getId();
        userBId = users.save(User.builder().name("Yarn B").email("yarn-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("yarnb").build()).getId();

        inventoryGroup = groupService.create("Yarn Test Inventory", userAId, Group.APPLET_MATERIAL_INVENTORY);
        inventoryGroup = groupService.addMember(inventoryGroup.getId(), userBId);
    }

    @AfterEach
    void cleanUp() {
        entries.findByGroupId(inventoryGroup.getId()).forEach(e -> entries.deleteById(e.getId()));
        yarnTypes.findByGroupId(inventoryGroup.getId()).forEach(y -> yarnTypes.deleteById(y.getId()));
        groups.deleteById(inventoryGroup.getId());
        users.deleteById(userAId);
        users.deleteById(userBId);
    }

    private YarnTypeView createWool() {
        return yarnTypeService.create(inventoryGroup.getId(), userAId,
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null));
    }

    @Test
    void anyMemberCanCreateAndEditAYarnType() {
        YarnTypeView created = createWool();
        assertThat(created.brand()).isEqualTo("Red Heart");

        YarnTypeView updated = yarnTypeService.update(inventoryGroup.getId(), userBId, created.id(),
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", "slightly faded"));
        assertThat(updated.notes()).isEqualTo("slightly faded");
    }

    @Test
    void rejectsADuplicateBrandThicknessColourCombination() {
        createWool();

        assertThatThrownBy(() -> yarnTypeService.create(inventoryGroup.getId(), userBId,
                new CreateYarnTypeRequest("red heart", "worsted (4)", "sunflower yellow", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void eachPersonSetsTheirOwnQuantityAndEveryoneCanSeeEveryonesInventory() {
        YarnTypeView wool = createWool();

        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 2.5);
        inventoryService.setMyQuantity(inventoryGroup.getId(), userBId, wool.id(), 0.25);

        List<InventoryEntryView> all = inventoryService.listAll(inventoryGroup.getId(), userAId);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(InventoryEntryView::userId, InventoryEntryView::quantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(userAId, 2.5),
                        org.assertj.core.groups.Tuple.tuple(userBId, 0.25));

        List<InventoryEntryView> mine = inventoryService.mine(inventoryGroup.getId(), userBId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).quantity()).isEqualTo(0.25);
    }

    @Test
    void rejectsAQuantityThatIsNotAQuarterSkeinStep() {
        YarnTypeView wool = createWool();

        assertThatThrownBy(() -> inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("quarter-skein");
    }

    @Test
    void settingQuantityToZeroRemovesTheEntry() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);
        assertThat(inventoryService.mine(inventoryGroup.getId(), userAId)).hasSize(1);

        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 0);

        assertThat(inventoryService.mine(inventoryGroup.getId(), userAId)).isEmpty();
    }

    @Test
    void cannotDeleteAYarnTypeStillPresentInSomeonesInventory() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);

        assertThatThrownBy(() -> yarnTypeService.delete(inventoryGroup.getId(), userAId, wool.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("still in");
    }

    @Test
    void deletingAnUnusedYarnTypeSucceeds() {
        YarnTypeView wool = createWool();

        yarnTypeService.delete(inventoryGroup.getId(), userAId, wool.id());

        assertThat(yarnTypeService.list(inventoryGroup.getId(), userAId)).isEmpty();
    }

    @Test
    void aFreshlySetQuantityIsNotFlaggedStale() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);

        InventoryEntryView view = inventoryService.mine(inventoryGroup.getId(), userAId).get(0);

        assertThat(view.stale()).isFalse();
    }

    @Test
    void anEntryUntouchedForOverThirtyDaysIsFlaggedStale() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);
        var entry = entries.findByGroupIdAndUserIdAndYarnTypeId(inventoryGroup.getId(), userAId, wool.id()).orElseThrow();
        entry.setUpdatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        entries.save(entry);

        InventoryEntryView view = inventoryService.mine(inventoryGroup.getId(), userAId).get(0);

        assertThat(view.stale()).isTrue();
    }
}
