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

    /** The only applet that exists today; Order Tracker adds its own key in Phase 6. */
    public static final String APPLET_BACKLOG_TRACKER = "backlogtracker";

    @Id
    private String id;

    private String name;

    /** Audit only — confers no special powers. */
    private String createdByUserId;

    /** Which applet this group belongs to (design: platform integration — a group is a
     *  separate "business"/workspace scoped to exactly one applet, never shared). */
    private String appletKey;

    @Indexed
    @Builder.Default
    private List<String> memberIds = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    public boolean hasMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }
}
