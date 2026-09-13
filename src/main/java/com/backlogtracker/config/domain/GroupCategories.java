package com.backlogtracker.config.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One group's own category labels — deliberately not on the shared {@code Group} document
 * (design: platform integration). A finance tracker and a personal to-do list have
 * nothing in common here, so each group gets its own list, keyed by groupId. No document
 * exists yet for a group that has never edited its categories — {@code GroupCategoryService}
 * falls back to {@link #DEFAULTS} in that case rather than eagerly seeding one on creation
 * (which would need Backlog Tracker's config code to hook into commons' group-creation flow).
 */
@Document("groupCategories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCategories {

    public static final List<String> DEFAULTS = List.of(
            "Research", "Skill-Building", "Project", "Technical Discussion", "Admin-Ops", "Other");

    @Id
    private String groupId;

    private List<String> categories;

    public static GroupCategories defaultsFor(String groupId) {
        return GroupCategories.builder().groupId(groupId).categories(new ArrayList<>(DEFAULTS)).build();
    }
}
