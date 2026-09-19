package com.backlogtracker.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import com.backlogtracker.materialinventory.inventory.service.InventoryService;
import com.backlogtracker.materialinventory.needle.domain.NeedleKind;
import com.backlogtracker.materialinventory.needle.dto.CreateNeedleTypeRequest;
import com.backlogtracker.materialinventory.needle.repository.NeedleInventoryEntryRepository;
import com.backlogtracker.materialinventory.needle.repository.NeedleTypeRepository;
import com.backlogtracker.materialinventory.needle.service.NeedleInventoryService;
import com.backlogtracker.materialinventory.needle.service.NeedleTypeService;
import com.backlogtracker.materialinventory.yarn.dto.CreateYarnTypeRequest;
import com.backlogtracker.materialinventory.yarn.repository.YarnTypeRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

/** to-3: YarnTypeService and NeedleTypeService follow the identical CRUD-template shape
 *  (create/update/delete/list, reject a duplicate identity combination case-insensitively,
 *  refuse deleting a type still present in someone's inventory) that bdup-2 explicitly
 *  left un-deduplicated in production code (different domain fields, marginal savings for
 *  real risk). The tests for that shared shape don't carry that same production-code risk,
 *  so they're shared here via a small fixture parameterized over both services -- each
 *  service's own test file keeps only what's genuinely specific to it (yarn's cost history,
 *  needle's crochet-hook-vs-knitting-needle distinction, quantity/concurrency behavior). */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeCrudContractTest {

    @Autowired GroupService groupService;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;

    @Autowired YarnTypeService yarnTypeService;
    @Autowired YarnTypeRepository yarnTypes;
    @Autowired InventoryService yarnInventoryService;

    @Autowired NeedleTypeService needleTypeService;
    @Autowired NeedleTypeRepository needleTypes;
    @Autowired NeedleInventoryService needleInventoryService;
    @Autowired NeedleInventoryEntryRepository needleEntries;

    private interface Fixture {
        Object create();
        void createDuplicateOfSameIdentity(); // must throw ResponseStatusException("already exists")
        Object edit(Object created);
        String editedNotes(Object edited);
        void markInUse(Object created);
        void delete(Object created); // must throw ResponseStatusException("still in") when in use
        void deleteCleanly(Object created);
        int listSize();
    }

    private String userAId;
    private String userBId;
    private Group group;

    @BeforeEach
    void setUp() {
        userAId = users.save(User.builder().name("TypeCrud A").email("typecrud-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("typecruda").build()).getId();
        userBId = users.save(User.builder().name("TypeCrud B").email("typecrud-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("typecrudb").build()).getId();
        group = groupService.create("TypeCrud Test Inventory", userAId, Group.APPLET_MATERIAL_INVENTORY);
        group = groupService.addMember(group.getId(), userBId);
    }

    @AfterEach
    void cleanUp() {
        needleEntries.findByGroupId(group.getId()).forEach(e -> needleEntries.deleteById(e.getId()));
        needleTypes.findByGroupId(group.getId()).forEach(n -> needleTypes.deleteById(n.getId()));
        yarnTypes.findByGroupId(group.getId()).forEach(y -> yarnTypes.deleteById(y.getId()));
        groups.deleteById(group.getId());
        users.deleteById(userAId);
        users.deleteById(userBId);
    }

    private Fixture yarnFixture() {
        return new Fixture() {
            @Override public Object create() {
                return yarnTypeService.create(group.getId(), userAId,
                        new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow",
                                null, null, null, null, null, null));
            }
            @Override public void createDuplicateOfSameIdentity() {
                yarnTypeService.create(group.getId(), userBId,
                        new CreateYarnTypeRequest("red heart", "worsted (4)", "sunflower yellow",
                                null, null, null, null, null, null));
            }
            @Override public Object edit(Object created) {
                var c = (com.backlogtracker.materialinventory.yarn.dto.YarnTypeView) created;
                return yarnTypeService.update(group.getId(), userBId, c.id(),
                        new CreateYarnTypeRequest("Red Heart", "Worsted (4)", "Sunflower Yellow",
                                null, null, null, null, "edited via contract test", null));
            }
            @Override public String editedNotes(Object edited) {
                return ((com.backlogtracker.materialinventory.yarn.dto.YarnTypeView) edited).notes();
            }
            @Override public void markInUse(Object created) {
                var c = (com.backlogtracker.materialinventory.yarn.dto.YarnTypeView) created;
                yarnInventoryService.setMyQuantity(group.getId(), userAId, c.id(), 1.0);
            }
            @Override public void delete(Object created) {
                var c = (com.backlogtracker.materialinventory.yarn.dto.YarnTypeView) created;
                yarnTypeService.delete(group.getId(), userAId, c.id());
            }
            @Override public void deleteCleanly(Object created) {
                delete(created);
            }
            @Override public int listSize() {
                return yarnTypeService.list(group.getId(), userAId).size();
            }
        };
    }

    private Fixture needleFixture() {
        return new Fixture() {
            @Override public Object create() {
                return needleTypeService.create(group.getId(), userAId,
                        new CreateNeedleTypeRequest(NeedleKind.CROCHET_HOOK, "4mm / US H-8", null));
            }
            @Override public void createDuplicateOfSameIdentity() {
                needleTypeService.create(group.getId(), userBId,
                        new CreateNeedleTypeRequest(NeedleKind.CROCHET_HOOK, "4mm / us h-8", null));
            }
            @Override public Object edit(Object created) {
                var c = (com.backlogtracker.materialinventory.needle.dto.NeedleTypeView) created;
                return needleTypeService.update(group.getId(), userBId, c.id(),
                        new CreateNeedleTypeRequest(NeedleKind.CROCHET_HOOK, "4mm / US H-8", "edited via contract test"));
            }
            @Override public String editedNotes(Object edited) {
                return ((com.backlogtracker.materialinventory.needle.dto.NeedleTypeView) edited).notes();
            }
            @Override public void markInUse(Object created) {
                var c = (com.backlogtracker.materialinventory.needle.dto.NeedleTypeView) created;
                needleInventoryService.setMyQuantity(group.getId(), userAId, c.id(), 1);
            }
            @Override public void delete(Object created) {
                var c = (com.backlogtracker.materialinventory.needle.dto.NeedleTypeView) created;
                needleTypeService.delete(group.getId(), userAId, c.id());
            }
            @Override public void deleteCleanly(Object created) {
                delete(created);
            }
            @Override public int listSize() {
                return needleTypeService.list(group.getId(), userAId).size();
            }
        };
    }

    Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("yarn", (Supplier<Fixture>) this::yarnFixture),
                Arguments.of("needle", (Supplier<Fixture>) this::needleFixture));
    }

    @ParameterizedTest(name = "{0}: any member can create and edit")
    @MethodSource("fixtures")
    void anyMemberCanCreateAndEditAType(String label, Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        Object created = f.create();
        Object edited = f.edit(created);
        assertThat(f.editedNotes(edited)).isEqualTo("edited via contract test");
    }

    @ParameterizedTest(name = "{0}: rejects a duplicate identity combination case-insensitively")
    @MethodSource("fixtures")
    void rejectsADuplicateIdentityCombinationCaseInsensitively(String label, Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        f.create();
        assertThatThrownBy(f::createDuplicateOfSameIdentity)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @ParameterizedTest(name = "{0}: cannot delete a type still present in someone's inventory")
    @MethodSource("fixtures")
    void cannotDeleteATypeStillPresentInSomeonesInventory(String label, Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        Object created = f.create();
        f.markInUse(created);

        assertThatThrownBy(() -> f.delete(created))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("still in");
    }

    @ParameterizedTest(name = "{0}: deleting an unused type succeeds")
    @MethodSource("fixtures")
    void deletingAnUnusedTypeSucceeds(String label, Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        Object created = f.create();

        f.deleteCleanly(created);

        assertThat(f.listSize()).isZero();
    }
}
