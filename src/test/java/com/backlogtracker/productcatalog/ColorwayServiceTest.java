package com.backlogtracker.productcatalog;

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
import com.backlogtracker.productcatalog.colorway.dto.ColorwayView;
import com.backlogtracker.productcatalog.colorway.dto.CreateColorwayRequest;
import com.backlogtracker.productcatalog.colorway.repository.ColorwayRepository;
import com.backlogtracker.productcatalog.colorway.service.ColorwayService;

@SpringBootTest
class ColorwayServiceTest {

    @Autowired GroupService groupService;
    @Autowired ColorwayService colorwayService;
    @Autowired ColorwayRepository colorways;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;

    private String userAId;
    private String userBId;
    private Group catalogGroup;

    @BeforeEach
    void setUp() {
        userAId = users.save(User.builder().name("Catalog A").email("catalog-a@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("cataloga").build()).getId();
        userBId = users.save(User.builder().name("Catalog B").email("catalog-b@x.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("catalogb").build()).getId();

        catalogGroup = groupService.create("Catalog Test Group", userAId, Group.APPLET_PRODUCT_CATALOG);
        catalogGroup = groupService.addMember(catalogGroup.getId(), userBId);
    }

    @AfterEach
    void cleanUp() {
        colorways.findByGroupId(catalogGroup.getId()).forEach(c -> colorways.deleteById(c.getId()));
        groups.deleteById(catalogGroup.getId());
        users.deleteById(userAId);
        users.deleteById(userBId);
    }

    private ColorwayView createSunsetRed() {
        return colorwayService.create(catalogGroup.getId(), userAId,
                new CreateColorwayRequest("Sunset", "Red", null, null, null));
    }

    @Test
    void newColorwayStartsInTheIdeaBox() {
        ColorwayView created = createSunsetRed();
        assertThat(created.ideabox()).isTrue();
    }

    @Test
    void anyMemberCanCreateAndEditAColorway() {
        ColorwayView created = createSunsetRed();
        assertThat(created.name()).isEqualTo("Sunset");

        ColorwayView updated = colorwayService.update(catalogGroup.getId(), userBId, created.id(),
                new CreateColorwayRequest("Sunset", "Red", 12.5, "runs small", List.of("Chain 20", "Row 1: sc across")));
        assertThat(updated.estimatedCost()).isEqualTo(12.5);
        assertThat(updated.notes()).isEqualTo("runs small");
        assertThat(updated.pattern().recipeSteps()).containsExactly("Chain 20", "Row 1: sc across");
    }

    @Test
    void rejectsADuplicateNameColourCombination() {
        createSunsetRed();

        assertThatThrownBy(() -> colorwayService.create(catalogGroup.getId(), userBId,
                new CreateColorwayRequest("sunset", "red", null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void promotingFlipsTheIdeaboxFlagAndNothingElse() {
        ColorwayView created = createSunsetRed();

        ColorwayView promoted = colorwayService.promote(catalogGroup.getId(), userAId, created.id());

        assertThat(promoted.ideabox()).isFalse();
        assertThat(promoted.name()).isEqualTo(created.name());
        assertThat(promoted.colour()).isEqualTo(created.colour());
    }

    @Test
    void omittingRecipeStepsLeavesPatternNull() {
        ColorwayView created = createSunsetRed();
        assertThat(created.pattern()).isNull();
    }

    @Test
    void deletingAColorwaySucceeds() {
        ColorwayView created = createSunsetRed();

        colorwayService.delete(catalogGroup.getId(), userAId, created.id());

        assertThat(colorwayService.list(catalogGroup.getId(), userAId)).isEmpty();
    }
}
