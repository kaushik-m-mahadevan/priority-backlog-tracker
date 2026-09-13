package com.backlogtracker.backlogtracker.config.service;

import java.util.List;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.config.domain.GroupCategories;
import com.backlogtracker.backlogtracker.config.repository.GroupCategoriesRepository;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Add/remove a group's own categories, following the same §2 safety rule as priorities:
 * more than one item uses the value → blocked; exactly one → reassignment required; none
 * → removed outright. Lives in Backlog Tracker's own config package (not on GroupService)
 * since categories are this applet's data, not a commons concept — see
 * {@link GroupCategories}.
 */
@Service
@RequiredArgsConstructor
public class GroupCategoryService {

    private final GroupCategoriesRepository repository;
    private final GroupService groupService;
    private final ItemRepository items;
    private final MongoOperations mongo;

    /** The effective list for a group — its own saved document, or the shared defaults
     *  if it has never edited them. */
    public List<String> categoriesFor(String groupId) {
        return repository.findById(groupId)
                .map(GroupCategories::getCategories)
                .orElse(GroupCategories.DEFAULTS);
    }

    public List<String> addCategory(String groupId, String userId, String name) {
        groupService.requireMember(groupId, userId);
        String n = requireName(name);
        GroupCategories doc = current(groupId);
        if (doc.getCategories().contains(n)) {
            throw new IllegalArgumentException("Category '" + n + "' already exists");
        }
        doc.getCategories().add(n);
        return repository.save(doc).getCategories();
    }

    public List<String> removeCategory(String groupId, String userId, String name, String reassignTo) {
        groupService.requireMember(groupId, userId);
        String n = requireName(name);
        GroupCategories doc = current(groupId);
        if (!doc.getCategories().contains(n)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such category: " + n);
        }
        if (doc.getCategories().size() <= 1) {
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
            if (reassignTo.equals(n) || !doc.getCategories().contains(reassignTo)) {
                throw new IllegalArgumentException("reassignTo must be another existing category");
            }
            mongo.updateMulti(
                    Query.query(Criteria.where("groupId").is(groupId).and("category").is(n)),
                    new Update().set("category", reassignTo), Item.class);
        }
        doc.getCategories().remove(n);
        return repository.save(doc).getCategories();
    }

    /** The group's saved document, seeded from defaults on first edit (not eagerly on
     *  group creation — see {@link GroupCategories}'s javadoc). */
    private GroupCategories current(String groupId) {
        return repository.findById(groupId).orElseGet(() -> GroupCategories.defaultsFor(groupId));
    }

    private static String requireName(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("A category name is required");
        }
        return s.trim();
    }
}
