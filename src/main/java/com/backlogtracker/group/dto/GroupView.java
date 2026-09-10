package com.backlogtracker.group.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.group.domain.Group;
import com.backlogtracker.user.dto.UserSummary;

/** A group with its members resolved for display. */
public record GroupView(String id, String name, Instant createdAt, List<UserSummary> members) {

    public static GroupView of(Group g, List<UserSummary> members) {
        return new GroupView(g.getId(), g.getName(), g.getCreatedAt(), members);
    }
}
