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
     *  if it has never edited them. For the controller; checks membership itself. */
    public List<String> categoriesFor(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return effectiveCategories(groupId);
    }

    /** Same lookup, no membership check — for internal callers (e.g. ItemService's own
     *  category validation) that are already downstream of their own membership check on
     *  the same request and would otherwise redo it pointlessly. */
    public List<String> effectiveCategories(String groupId) {
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
        SafeRemovalRule.requireAtLeastOneRemains(doc.getCategories().size(), "category");
        long used = items.countByGroupIdAndCategory(groupId, n);
        String target = SafeRemovalRule.checkUsageAndValidateReassign(
                used, () -> affectedTitles(groupId, n), reassignTo, doc.getCategories(), n, "category");
        if (target != null) {
            mongo.updateMulti(
                    Query.query(Criteria.where("groupId").is(groupId).and("category").is(n)),
                    new Update().set("category", target), Item.class);
        }
        doc.getCategories().remove(n);
        return repository.save(doc).getCategories();
    }

    private List<String> affectedTitles(String groupId, String category) {
        return items.findByGroupIdAndCategory(groupId, category).stream()
                .map(Item::getTitle).limit(8).toList();
    }

    /** The group's saved document, seeded from defaults on first edit (not eagerly on
     *  group creation — see {@link GroupCategories}'s javadoc). */
    private GroupCategories current(String groupId) {
        return repository.findById(groupId).orElseGet(() -> GroupCategories.defaultsFor(groupId));
    }

    private static String requireName(String s) {
        return SafeRemovalRule.requireNonBlank(s, "category");
    }
}
