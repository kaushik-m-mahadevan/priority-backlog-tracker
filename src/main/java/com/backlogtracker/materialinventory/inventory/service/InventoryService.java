package com.backlogtracker.materialinventory.inventory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.materialinventory.inventory.domain.InventoryEntry;
import com.backlogtracker.materialinventory.inventory.dto.InventoryEntryView;
import com.backlogtracker.materialinventory.inventory.repository.InventoryEntryRepository;
import com.backlogtracker.materialinventory.yarn.service.YarnTypeService;

import lombok.RequiredArgsConstructor;

/**
 * Each member's own on-hand yarn quantities, snapshot-style (set directly, not
 * accumulated) — see {@link InventoryEntry} for the visibility and ownership rules.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    /** Quantities are in skeins and must land on a quarter-skein step (design decision) —
     *  compared with a small epsilon since the value arrives as a double. */
    private static final double QUARTER_STEP_EPSILON = 1e-9;

    /** How long an entry can go untouched before it's flagged "stale" in the UI (design
     *  decision: tie staleness to activity). This approximates activity using the entry's
     *  own {@code updatedAt} rather than a real cross-applet signal (an order intake or
     *  completion in Order Tracker, say) — that would need new commons plumbing between
     *  applets that otherwise don't depend on each other, out of scope for a first version.
     *  30 days is a judgment call: long enough that normal weekly crafting activity doesn't
     *  spuriously flag everything, short enough to actually catch inventory nobody's
     *  touched in a while. Revisit if a real cross-applet activity signal gets built later. */
    private static final Duration STALE_AFTER = Duration.ofDays(30);

    private final InventoryEntryRepository repository;
    private final YarnTypeService yarnTypeService;
    private final GroupService groupService;
    private final Clock clock;

    /** Business-wide — any member can see everyone's inventory (design decision: full
     *  transparency, same precedent as Finance Tracker's balances), not just their own. */
    public List<InventoryEntryView> listAll(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupId(groupId).stream().map(this::toView).toList();
    }

    public List<InventoryEntryView> mine(String groupId, String userId) {
        groupService.requireMember(groupId, userId);
        return repository.findByGroupIdAndUserId(groupId, userId).stream().map(this::toView).toList();
    }

    private InventoryEntryView toView(InventoryEntry e) {
        boolean stale = e.getUpdatedAt() != null
                && Duration.between(e.getUpdatedAt(), Instant.now(clock)).compareTo(STALE_AFTER) > 0;
        return InventoryEntryView.of(e, stale);
    }

    /** Setting quantity to exactly 0 removes the row entirely rather than keeping a
     *  zero-quantity entry around — a business-wide list of "who has what" is more useful
     *  when it only lists what's actually on hand. */
    public void setMyQuantity(String groupId, String userId, String yarnTypeId, double quantity) {
        groupService.requireMember(groupId, userId);
        yarnTypeService.requireById(groupId, yarnTypeId);
        applyQuantity(groupId, userId, yarnTypeId, requireQuarterStep(quantity));
    }

    public double quantityOf(String groupId, String userId, String yarnTypeId) {
        return repository.findByGroupIdAndUserIdAndYarnTypeId(groupId, userId, yarnTypeId)
                .map(InventoryEntry::getQuantity).orElse(0.0);
    }

    /** Used internally by cross-user workflows (yarn transfers) that need to move a
     *  specific amount between two people's on-hand quantities — the caller here is
     *  trusted application code that has already authorized the action itself (e.g. only
     *  the actual holder of the yarn may trigger a transfer out of their own row), not a
     *  raw self-service request, so there's no "must be yourself" check. {@code delta} may
     *  be negative (yarn leaving) or positive (yarn arriving). */
    public void adjustQuantity(String groupId, String userId, String yarnTypeId, double delta) {
        double updated = quantityOf(groupId, userId, yarnTypeId) + delta;
        if (updated < -QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough on hand for this transfer");
        }
        applyQuantity(groupId, userId, yarnTypeId, requireQuarterStep(Math.max(0, updated)));
    }

    private double requireQuarterStep(double quantity) {
        if (quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must not be negative");
        }
        double quarters = quantity * 4;
        if (Math.abs(quarters - Math.round(quarters)) > QUARTER_STEP_EPSILON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be in quarter-skein steps (e.g. 0.25, 1.5)");
        }
        return Math.round(quarters) / 4.0;
    }

    private void applyQuantity(String groupId, String userId, String yarnTypeId, double quantity) {
        InventoryEntry existing = repository.findByGroupIdAndUserIdAndYarnTypeId(groupId, userId, yarnTypeId).orElse(null);
        if (quantity == 0) {
            if (existing != null) {
                repository.deleteById(existing.getId());
            }
            return;
        }
        if (existing != null) {
            existing.setQuantity(quantity);
            existing.setUpdatedAt(Instant.now(clock));
            repository.save(existing);
            return;
        }
        repository.save(InventoryEntry.builder()
                .groupId(groupId)
                .userId(userId)
                .yarnTypeId(yarnTypeId)
                .quantity(quantity)
                .updatedAt(Instant.now(clock))
                .build());
    }
}
