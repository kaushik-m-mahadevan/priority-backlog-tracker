package com.backlogtracker.backlogtracker.item.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.backlogtracker.config.service.ConfigService;
import com.backlogtracker.backlogtracker.config.service.GroupCategoryService;
import com.backlogtracker.commons.counter.CounterService;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.backlogtracker.item.domain.Notes;
import com.backlogtracker.backlogtracker.item.dto.CreateItemRequest;
import com.backlogtracker.backlogtracker.item.dto.UpdateItemRequest;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * CRUD and live status flow for backlog items. Every item belongs to one group; the
 * caller must be a member of that group to read or mutate it (design: groups).
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private static final List<ItemStatus> LIVE = List.of(ItemStatus.BACKLOG, ItemStatus.IN_PROGRESS);

    private final ItemRepository items;
    private final ConfigService configService;
    private final GroupCategoryService groupCategoryService;
    private final CounterService counters;
    private final UserRepository users;
    private final GroupService groupService;
    private final Clock clock;

    public Item create(CreateItemRequest r, AuthUser actor) {
        groupService.requireMember(r.groupId(), actor.id());
        AppConfig cfg = configService.getConfig();
        validateCategory(r.groupId(), r.category());
        validatePriority(cfg, r.priority());
        validateOwner(r.ownerId());
        if (r.linkedOrderId() != null && !r.linkedOrderId().isBlank()
                && items.findByGroupIdAndLinkedOrderId(r.groupId(), r.linkedOrderId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This order is already linked to an item in this group");
        }

        Instant due = r.dueDate() != null
                ? r.dueDate()
                : clock.instant().plus(Duration.ofDays(cfg.getDefaultDueDateOffsetDays()));

        Item item = Item.builder()
                .itemId(counters.nextSharedItemId())
                .title(r.title().trim())
                .category(r.category())
                .priority(r.priority())
                .effortEstimate(r.effortEstimate())
                .dueDate(due)
                .status(ItemStatus.BACKLOG)
                .groupId(r.groupId())
                .createdBy(actor.id())
                .lastUpdatedBy(actor.id())
                .ownerId(blankToNull(r.ownerId()))
                .linkedOrderId(blankToNull(r.linkedOrderId()))
                .build();
        if (r.notes() != null && !r.notes().isBlank()) {
            item.setNotes(Notes.markdown(r.notes().trim()));
        }
        return items.save(item);
    }

    /** Used only by Order Tracker's "add to group" flow (called directly from the browser,
     *  not from any ordertracker backend code — this package never imports ordertracker). */
    public Item findByLinkedOrder(String groupId, String linkedOrderId, AuthUser actor) {
        groupService.requireMember(groupId, actor.id());
        return items.findByGroupIdAndLinkedOrderId(groupId, linkedOrderId).orElse(null);
    }

    public Item update(String id, UpdateItemRequest r, AuthUser actor) {
        Item item = requireMemberItem(id, actor);
        AppConfig cfg = configService.getConfig();
        validateCategory(item.getGroupId(), r.category());
        validatePriority(cfg, r.priority());
        validateOwner(r.ownerId());

        // carry the version the client saw so a concurrent edit is rejected (409)
        if (r.version() != null) {
            item.setVersion(r.version());
        }
        item.setTitle(r.title().trim());
        item.setCategory(r.category());
        item.setPriority(r.priority());
        item.setEffortEstimate(r.effortEstimate());
        item.setDueDate(r.dueDate());
        item.setOwnerId(blankToNull(r.ownerId()));
        applyNotes(item, r.notes());
        item.setLastUpdatedBy(actor.id());
        return items.save(item);
    }

    /** null = leave notes as-is; "" = clear; otherwise replace when the text changed. */
    private static void applyNotes(Item item, String notes) {
        if (notes == null) {
            return;
        }
        String current = item.getNotes() == null ? "" : item.getNotes().getContent();
        String next = notes.trim();
        if (next.equals(current == null ? "" : current)) {
            return;
        }
        item.setNotes(next.isEmpty() ? null : Notes.markdown(next));
    }

    public Item changeStatus(String id, ItemStatus status, AuthUser actor) {
        Item item = requireMemberItem(id, actor);
        item.setStatus(status);
        item.setLastUpdatedBy(actor.id());
        return items.save(item);
    }

    public Item get(String id, AuthUser actor) {
        return requireMemberItem(id, actor);
    }

    /** Pin / unpin an item for the whole group (design: pinning). Any member may. */
    public Item setPinned(String id, AuthUser actor, boolean pinned) {
        Item item = requireMemberItem(id, actor);
        if (item.isPinned() == pinned) {
            return item;
        }
        item.setPinned(pinned);
        item.setPinnedAt(pinned ? java.time.Instant.now() : null);
        item.setPinnedByUserId(pinned ? actor.id() : null);
        return items.save(item);
    }

    /** Live items (Backlog + In Progress) in a group. Caller membership checked upstream. */
    public List<Item> listLive(String groupId) {
        return items.findByGroupIdAndStatusIn(groupId, LIVE);
    }

    private Item requireMemberItem(String id, AuthUser actor) {
        Item item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Item not found: " + id));
        groupService.requireMember(item.getGroupId(), actor.id());
        return item;
    }

    private void validateCategory(String groupId, String category) {
        List<String> allowed = groupCategoryService.categoriesFor(groupId);
        if (!allowed.contains(category)) {
            throw new IllegalArgumentException(
                    "Unknown category '" + category + "'. Allowed: " + allowed);
        }
    }

    private static void validatePriority(AppConfig cfg, String priority) {
        if (!cfg.getPriorities().contains(priority)) {
            throw new IllegalArgumentException(
                    "Unknown priority '" + priority + "'. Allowed: " + cfg.getPriorities());
        }
    }

    private void validateOwner(String ownerId) {
        String id = blankToNull(ownerId);
        if (id != null && !users.existsById(id)) {
            throw new IllegalArgumentException("Unknown owner '" + ownerId + "'");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
