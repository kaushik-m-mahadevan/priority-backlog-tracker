package com.backlogtracker.item.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.service.ConfigService;
import com.backlogtracker.counter.CounterService;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.domain.ItemScope;
import com.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.item.dto.CreateItemRequest;
import com.backlogtracker.item.dto.UpdateItemRequest;
import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * CRUD and live status flow for shared backlog items (design §2, §4). Personal-scope
 * items, edit locking, and terminal/archive transitions arrive in later steps.
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private static final List<ItemStatus> LIVE = List.of(ItemStatus.BACKLOG, ItemStatus.IN_PROGRESS);

    private final ItemRepository items;
    private final ConfigService configService;
    private final CounterService counters;
    private final Clock clock;

    public Item create(CreateItemRequest r, AuthUser actor) {
        AppConfig cfg = configService.getConfig();
        validateCategory(cfg, r.category());
        validatePriority(cfg, r.priority());

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
                .scope(ItemScope.SHARED)
                .createdBy(actor.id())
                .lastUpdatedBy(actor.id())
                .ownerId(blankToNull(r.ownerId()))
                .build();
        return items.save(item);
    }

    public Item update(String id, UpdateItemRequest r, AuthUser actor) {
        Item item = require(id);
        AppConfig cfg = configService.getConfig();
        validateCategory(cfg, r.category());
        validatePriority(cfg, r.priority());

        item.setTitle(r.title().trim());
        item.setCategory(r.category());
        item.setPriority(r.priority());
        item.setEffortEstimate(r.effortEstimate());
        item.setDueDate(r.dueDate());
        item.setOwnerId(blankToNull(r.ownerId()));
        item.setLastUpdatedBy(actor.id());
        return items.save(item);
    }

    public Item changeStatus(String id, ItemStatus status, AuthUser actor) {
        Item item = require(id);
        item.setStatus(status);
        item.setLastUpdatedBy(actor.id());
        return items.save(item);
    }

    public Item get(String id) {
        return require(id);
    }

    /** Live shared items (Backlog + In Progress). */
    public List<Item> listShared() {
        return items.findByScopeAndStatusIn(ItemScope.SHARED, LIVE);
    }

    private Item require(String id) {
        return items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
    }

    private static void validateCategory(AppConfig cfg, String category) {
        if (!cfg.getCategories().contains(category)) {
            throw new IllegalArgumentException(
                    "Unknown category '" + category + "'. Allowed: " + cfg.getCategories());
        }
    }

    private static void validatePriority(AppConfig cfg, String priority) {
        if (!cfg.getPriorities().contains(priority)) {
            throw new IllegalArgumentException(
                    "Unknown priority '" + priority + "'. Allowed: " + cfg.getPriorities());
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
