package com.backlogtracker.config.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.config.domain.ConfigHistory;
import com.backlogtracker.config.dto.UpdateConfigRequest;
import com.backlogtracker.config.repository.ConfigHistoryRepository;
import com.backlogtracker.config.repository.ConfigRepository;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read, seed, and edit the single {@link AppConfig} (design §7). Every change is written
 * to {@code configHistory} (§15). Category / priority list edits follow the §2 safety
 * rules: more than one item uses the value → blocked; exactly one → reassignment
 * required; none → removed outright.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository repository;
    private final ConfigHistoryRepository history;
    private final ItemRepository items;
    private final MongoOperations mongo;
    private final Clock clock;

    public AppConfig getConfig() {
        return repository.findById(AppConfig.SINGLETON_ID)
                .orElseGet(() -> repository.save(AppConfig.defaults()));
    }

    public AppConfig seedIfAbsent() {
        return getConfig();
    }

    public List<ConfigHistory> history() {
        return history.findTop30ByOrderByTimestampDesc();
    }

    // ---- weights & thresholds ------------------------------------------------------

    public AppConfig update(UpdateConfigRequest r, String actorId) {
        double sum = r.priorityWeight() + r.urgencyWeight() + r.effortWeight();
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException(
                    "priorityWeight + urgencyWeight + effortWeight must equal 1 (got " + sum + ")");
        }
        AppConfig cfg = getConfig();
        if (!cfg.getPriorities().containsAll(r.buriedPriorityLevels())) {
            throw new IllegalArgumentException(
                    "buriedPriorityLevels must all be existing priorities: " + cfg.getPriorities());
        }
        if (!cfg.getPriorities().containsAll(r.priorityValues().keySet())
                || !r.priorityValues().keySet().containsAll(cfg.getPriorities())) {
            throw new IllegalArgumentException(
                    "priorityValues keys must exactly match the priority list " + cfg.getPriorities());
        }

        Map<String, Object> before = snapshot(cfg);
        cfg.setPriorityWeight(r.priorityWeight());
        cfg.setUrgencyWeight(r.urgencyWeight());
        cfg.setEffortWeight(r.effortWeight());
        cfg.setUrgencyWindowDays(r.urgencyWindowDays());
        cfg.setStaleThresholdDays(r.staleThresholdDays());
        cfg.setBuriedThresholdDays(r.buriedThresholdDays());
        cfg.setDefaultDueDateOffsetDays(r.defaultDueDateOffsetDays());
        cfg.setEffortCapDays(r.effortCapDays());
        cfg.setBuriedPriorityLevels(new ArrayList<>(r.buriedPriorityLevels()));
        cfg.setPriorityValues(new LinkedHashMap<>(r.priorityValues()));
        AppConfig saved = repository.save(cfg);
        record(actorId, "weights & thresholds updated", before, snapshot(saved));
        return saved;
    }

    // ---- categories -------------------------------------------------------------

    public AppConfig addCategory(String name) {
        AppConfig cfg = getConfig();
        String n = require(name, "category");
        if (cfg.getCategories().contains(n)) {
            throw new IllegalArgumentException("Category '" + n + "' already exists");
        }
        Map<String, Object> before = snapshot(cfg);
        cfg.getCategories().add(n);
        AppConfig saved = repository.save(cfg);
        record(null, "added category '" + n + "'", before, snapshot(saved));
        return saved;
    }

    public AppConfig removeCategory(String name, String reassignTo, String actorId) {
        AppConfig cfg = getConfig();
        String n = require(name, "category");
        if (!cfg.getCategories().contains(n)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such category: " + n);
        }
        if (cfg.getCategories().size() <= 1) {
            throw new IllegalArgumentException("At least one category must remain");
        }
        long used = items.countByCategory(n);
        if (used > 1) {
            throw conflict("category", n, used);
        }
        if (used == 1) {
            String target = validReassign(reassignTo, cfg.getCategories(), n, "category");
            mongo.updateMulti(Query.query(Criteria.where("category").is(n)),
                    new Update().set("category", target), Item.class);
        }
        Map<String, Object> before = snapshot(cfg);
        cfg.getCategories().remove(n);
        AppConfig saved = repository.save(cfg);
        record(actorId, "removed category '" + n + "'"
                + (used == 1 ? " (1 item reassigned to '" + reassignTo + "')" : ""),
                before, snapshot(saved));
        return saved;
    }

    // ---- priorities -----------------------------------------------------------

    public AppConfig addPriority(String name, Integer valueWeight) {
        AppConfig cfg = getConfig();
        String n = require(name, "priority");
        if (cfg.getPriorities().contains(n)) {
            throw new IllegalArgumentException("Priority '" + n + "' already exists");
        }
        if (valueWeight == null || valueWeight < 1) {
            throw new IllegalArgumentException("A priority needs a positive numeric weight");
        }
        Map<String, Object> before = snapshot(cfg);
        cfg.getPriorities().add(n);
        cfg.getPriorityValues().put(n, valueWeight);
        AppConfig saved = repository.save(cfg);
        record(null, "added priority '" + n + "' = " + valueWeight, before, snapshot(saved));
        return saved;
    }

    public AppConfig removePriority(String name, String reassignTo, String actorId) {
        AppConfig cfg = getConfig();
        String n = require(name, "priority");
        if (!cfg.getPriorities().contains(n)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such priority: " + n);
        }
        if (cfg.getPriorities().size() <= 1) {
            throw new IllegalArgumentException("At least one priority must remain");
        }
        long used = items.countByPriority(n);
        if (used > 1) {
            throw conflict("priority", n, used);
        }
        if (used == 1) {
            String target = validReassign(reassignTo, cfg.getPriorities(), n, "priority");
            mongo.updateMulti(Query.query(Criteria.where("priority").is(n)),
                    new Update().set("priority", target), Item.class);
        }
        Map<String, Object> before = snapshot(cfg);
        cfg.getPriorities().remove(n);
        cfg.getPriorityValues().remove(n);
        cfg.getBuriedPriorityLevels().remove(n);
        AppConfig saved = repository.save(cfg);
        record(actorId, "removed priority '" + n + "'"
                + (used == 1 ? " (1 item reassigned to '" + reassignTo + "')" : ""),
                before, snapshot(saved));
        return saved;
    }

    // ---- helpers ---------------------------------------------------------------

    private static String require(String s, String what) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("A " + what + " name is required");
        }
        return s.trim();
    }

    private static String validReassign(String reassignTo, List<String> allowed, String removing,
                                        String what) {
        if (reassignTo == null || reassignTo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "One item uses this " + what + " — supply reassignTo to move it first");
        }
        if (reassignTo.equals(removing) || !allowed.contains(reassignTo)) {
            throw new IllegalArgumentException("reassignTo must be another existing " + what);
        }
        return reassignTo;
    }

    private ResponseStatusException conflict(String what, String name, long used) {
        List<String> titles = items.findAll().stream()
                .filter(i -> name.equals(what.equals("category") ? i.getCategory() : i.getPriority()))
                .map(Item::getTitle)
                .limit(8)
                .toList();
        return new ResponseStatusException(HttpStatus.CONFLICT,
                used + " items use the " + what + " '" + name + "' — reassign them first: " + titles);
    }

    private static Map<String, Object> snapshot(AppConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priorityWeight", c.getPriorityWeight());
        m.put("urgencyWeight", c.getUrgencyWeight());
        m.put("effortWeight", c.getEffortWeight());
        m.put("urgencyWindowDays", c.getUrgencyWindowDays());
        m.put("staleThresholdDays", c.getStaleThresholdDays());
        m.put("buriedThresholdDays", c.getBuriedThresholdDays());
        m.put("defaultDueDateOffsetDays", c.getDefaultDueDateOffsetDays());
        m.put("effortCapDays", c.getEffortCapDays());
        m.put("buriedPriorityLevels", new ArrayList<>(c.getBuriedPriorityLevels()));
        m.put("categories", new ArrayList<>(c.getCategories()));
        m.put("priorities", new ArrayList<>(c.getPriorities()));
        m.put("priorityValues", new LinkedHashMap<>(c.getPriorityValues()));
        return m;
    }

    private void record(String actorId, String summary, Map<String, Object> before,
                        Map<String, Object> after) {
        history.save(ConfigHistory.builder()
                .changedBy(actorId)
                .timestamp(clock.instant())
                .summary(summary)
                .previousValues(before)
                .newValues(after)
                .build());
    }
}
