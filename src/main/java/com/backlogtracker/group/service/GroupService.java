package com.backlogtracker.group.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.group.domain.Group;
import com.backlogtracker.group.repository.GroupRepository;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.repository.ItemRepository;
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
    private final ItemRepository items;
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

    /** Number of members in a group, or 0 if it doesn't exist. */
    public int memberCount(String groupId) {
        return groups.findById(groupId).map(g -> g.getMemberIds().size()).orElse(0);
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
            long deletedItems = mongo.remove(new Query(Criteria.where("groupId").is(groupId)), "items")
                    .getDeletedCount();
            mongo.remove(new Query(Criteria.where("groupId").is(groupId)), "archivedItems");
            log.info("Group {} deleted by its last member; {} live items removed", groupId, deletedItems);
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

    // ---- categories --------------------------------------------------------------
    //
    // Deliberately per-group and NOT shared config: a finance tracker and a personal
    // to-do list have nothing in common here. Kept on GroupService (not ConfigService)
    // since the list is a property of the group document itself, not the shared
    // priority/weight config. Same §2 safety rule as priorities, applied to this
    // group's items only — unlike priorities, category edits aren't written to
    // configHistory, since that trail is specifically for the shared config.

    /** Add a category to this group's list. Any member may. */
    public Group addCategory(String groupId, String userId, String name) {
        Group g = requireMember(groupId, userId);
        String n = requireName(name);
        if (g.getCategories().contains(n)) {
            throw new IllegalArgumentException("Category '" + n + "' already exists");
        }
        g.getCategories().add(n);
        return groups.save(g);
    }

    /** Remove a category from this group's list, following the §2 safety rule. */
    public Group removeCategory(String groupId, String userId, String name, String reassignTo) {
        Group g = requireMember(groupId, userId);
        String n = requireName(name);
        if (!g.getCategories().contains(n)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such category: " + n);
        }
        if (g.getCategories().size() <= 1) {
            throw new IllegalArgumentException("At least one category must remain");
        }
        long used = items.countByGroupIdAndCategory(groupId, n);
        if (used > 1) {
            List<String> titles = items.findByGroupIdAndCategory(groupId, n).stream()
                    .map(Item::getTitle).limit(8).toList();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    used + " items use the category '" + n + "' — reassign them first: " + titles);
        }
        if (used == 1) {
            if (reassignTo == null || reassignTo.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "One item uses this category — supply reassignTo to move it first");
            }
            if (reassignTo.equals(n) || !g.getCategories().contains(reassignTo)) {
                throw new IllegalArgumentException("reassignTo must be another existing category");
            }
            mongo.updateMulti(
                    Query.query(Criteria.where("groupId").is(groupId).and("category").is(n)),
                    new Update().set("category", reassignTo), Item.class);
        }
        g.getCategories().remove(n);
        return groups.save(g);
    }

    private static String requireName(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("A category name is required");
        }
        return s.trim();
    }
}
