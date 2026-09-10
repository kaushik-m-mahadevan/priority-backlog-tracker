package com.backlogtracker.group.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.group.domain.Group;
import com.backlogtracker.group.repository.GroupRepository;
import com.backlogtracker.user.dto.UserSummary;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groups;
    private final ConfigService configService;
    private final UserRepository users;
    private final MongoOperations mongo;

    public List<Group> myGroups(String userId) {
        return groups.findByMemberIdsContaining(userId).stream()
                .sorted(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Group create(String name, String creatorId) {
        int cap = configService.getConfig().getMaxGroupsPerUser();
        if (cap > 0 && groups.countByMemberIdsContaining(creatorId) >= cap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You're already in the maximum of " + cap + " groups");
        }
        return groups.save(Group.builder()
                .name(name.trim())
                .createdByUserId(creatorId)
                .memberIds(new ArrayList<>(List.of(creatorId)))
                .build());
    }

    /** Rename a group. Any member may do it (design: groups are shared, owner-less). */
    public Group rename(String groupId, String userId, String name) {
        Group g = requireMember(groupId, userId);
        g.setName(name.trim());
        return groups.save(g);
    }

    /** The group, or 404 if missing / 403 if the caller isn't a member. */
    public Group requireMember(String groupId, String userId) {
        Group g = groups.findById(groupId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        if (!g.hasMember(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
        return g;
    }

    public boolean isMember(String groupId, String userId) {
        return groupId != null
                && groups.findById(groupId).map(g -> g.hasMember(userId)).orElse(false);
    }

    public long groupCount(String userId) {
        return groups.countByMemberIdsContaining(userId);
    }

    public int maxGroupsPerUser() {
        return configService.getConfig().getMaxGroupsPerUser();
    }

    /** Add a user to a group (used when they accept an invite). Re-checks the cap. */
    public Group addMember(String groupId, String userId) {
        Group g = groups.findById(groupId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        if (g.hasMember(userId)) {
            return g;
        }
        int cap = maxGroupsPerUser();
        if (cap > 0 && groupCount(userId) >= cap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You're already in the maximum of " + cap + " groups — leave one first");
        }
        g.getMemberIds().add(userId);
        return groups.save(g);
    }

    /** Remove the caller. If they were the last member, delete the group and all its items. */
    public void leave(String groupId, String userId) {
        Group g = requireMember(groupId, userId);
        g.getMemberIds().remove(userId);
        if (g.getMemberIds().isEmpty()) {
            groups.delete(g);
            long items = mongo.remove(new Query(Criteria.where("groupId").is(groupId)), "items")
                    .getDeletedCount();
            mongo.remove(new Query(Criteria.where("groupId").is(groupId)), "archivedItems");
            log.info("Group {} deleted by its last member; {} live items removed", groupId, items);
        } else {
            groups.save(g);
        }
    }

    public List<UserSummary> members(Group g) {
        return users.findAllById(g.getMemberIds()).stream()
                .map(UserSummary::of)
                .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
