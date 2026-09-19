package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, null, null));
    }

    @Test
    void anyMemberCanCreateAndEditAYarnType() {
        YarnTypeView created = createWool();
        assertThat(created.brand()).isEqualTo("Red Heart");

        YarnTypeView updated = yarnTypeService.update(inventoryGroup.getId(), userBId, created.id(),
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, "slightly faded", null));
        assertThat(updated.notes()).isEqualTo("slightly faded");
    }

    @Test
    void rejectsADuplicateBrandThicknessColourCombination() {
        createWool();

        assertThatThrownBy(() -> yarnTypeService.create(inventoryGroup.getId(), userBId,
                new CreateYarnTypeRequest("red heart", "worsted (4)", "sunflower yellow", null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void settingACostOnCreateRecordsTheFirstHistoryEntry() {
        YarnTypeView created = yarnTypeService.create(inventoryGroup.getId(), userAId,
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, null, 180.0));

        assertThat(created.costPerSkein()).isEqualTo(180.0);
        assertThat(created.costHistory()).hasSize(1);
        assertThat(created.costHistory().get(0).previousCost()).isNull();
        assertThat(created.costHistory().get(0).newCost()).isEqualTo(180.0);
    }

    @Test
    void changingCostOnUpdateAppendsAHistoryEntryButUnrelatedEditsDoNotAddSpuriousOnes() {
        YarnTypeView created = yarnTypeService.create(inventoryGroup.getId(), userAId,
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, null, 180.0));

        YarnTypeView sameCost = yarnTypeService.update(inventoryGroup.getId(), userAId, created.id(),
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, "re-saved", 180.0));
        assertThat(sameCost.costHistory()).hasSize(1);

        YarnTypeView priceIncrease = yarnTypeService.update(inventoryGroup.getId(), userAId, created.id(),
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, "re-saved", 200.0));
        assertThat(priceIncrease.costPerSkein()).isEqualTo(200.0);
        assertThat(priceIncrease.costHistory()).hasSize(2);
        assertThat(priceIncrease.costHistory().get(1).previousCost()).isEqualTo(180.0);
        assertThat(priceIncrease.costHistory().get(1).newCost()).isEqualTo(200.0);
    }

    @Test
    void clearingTheCostBackToNullAppendsAHistoryEntryToo() {
        YarnTypeView created = yarnTypeService.create(inventoryGroup.getId(), userAId,
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, null, 180.0));

        YarnTypeView cleared = yarnTypeService.update(inventoryGroup.getId(), userAId, created.id(),
                new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow", null, null, null, null, null, null));

        assertThat(cleared.costPerSkein()).isNull();
        assertThat(cleared.costHistory()).hasSize(2);
        assertThat(cleared.costHistory().get(1).previousCost()).isEqualTo(180.0);
        assertThat(cleared.costHistory().get(1).newCost()).isNull();
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

    @Test
    void adjustQuantityRejectsWithdrawingMoreThanOnHandWithoutTouchingTheRow() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);

        assertThatThrownBy(() -> inventoryService.adjustQuantity(inventoryGroup.getId(), userAId, wool.id(), -1.5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough on hand");

        assertThat(inventoryService.quantityOf(inventoryGroup.getId(), userAId, wool.id())).isEqualTo(1.0);
    }

    @Test
    void adjustQuantityWithdrawingDownToExactlyZeroDeletesTheRow() {
        YarnTypeView wool = createWool();
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), 1.0);

        inventoryService.adjustQuantity(inventoryGroup.getId(), userAId, wool.id(), -1.0);

        assertThat(inventoryService.mine(inventoryGroup.getId(), userAId)).isEmpty();
    }

    @Test
    void adjustQuantityDepositingCreatesARowWhenNoneExistsYet() {
        YarnTypeView wool = createWool();

        inventoryService.adjustQuantity(inventoryGroup.getId(), userBId, wool.id(), 0.75);

        assertThat(inventoryService.quantityOf(inventoryGroup.getId(), userBId, wool.id())).isEqualTo(0.75);
    }

    /** Regression test for a real race: adjustQuantity used to be a read-then-write
     *  (read quantity, compute new value, save), so concurrent withdrawals against the
     *  same row could both read the same starting quantity and drive it negative or
     *  lose one decrement. It's now a single atomic findAndModify $inc, so N concurrent
     *  1-skein withdrawals against a 50-skein starting balance must land on exactly 0,
     *  never negative and never short (same style as CounterServiceTest's concurrency
     *  check). */
    @Test
    void adjustQuantityUnderConcurrentWithdrawalsNeverGoesNegativeOrLosesAnUpdate() throws Exception {
        YarnTypeView wool = createWool();
        int n = 50;
        inventoryService.setMyQuantity(inventoryGroup.getId(), userAId, wool.id(), n);

        ExecutorService pool = Executors.newFixedThreadPool(n); // must fit every task at once for the latch below
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, n)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        ready.countDown();
                        go.await();
                        inventoryService.adjustQuantity(inventoryGroup.getId(), userAId, wool.id(), -1.0);
                        return null;
                    })
                    .toList();
            List<java.util.concurrent.Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(inventoryService.mine(inventoryGroup.getId(), userAId)).isEmpty();
    }
}
