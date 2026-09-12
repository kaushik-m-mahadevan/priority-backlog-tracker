package com.backlogtracker.group.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A shared workspace. No owner — every member has identical rights. Membership is an
 * embedded id list (small teams). The last member leaving deletes the group and its
 * items (design: groups).
 */
@Document("groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    private String id;

    private String name;

    /** Audit only — confers no special powers. */
    private String createdByUserId;

    @Indexed
    @Builder.Default
    private List<String> memberIds = new ArrayList<>();

    /**
     * This group's own category labels — deliberately not shared across groups. A
     * finance tracker and a personal to-do list have nothing in common here, so each
     * group starts from {@link #defaultCategories()} and edits its own list from there.
     */
    @Builder.Default
    private List<String> categories = defaultCategories();

    @CreatedDate
    private Instant createdAt;

    public boolean hasMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }

    /** Starting point for a newly created group; freely edited afterward. */
    public static List<String> defaultCategories() {
        return new ArrayList<>(List.of(
                "Research", "Skill-Building", "Project", "Technical Discussion", "Admin-Ops", "Other"));
    }
}
