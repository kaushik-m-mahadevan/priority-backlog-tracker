package com.backlogtracker.backlogtracker.config.service;

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

import com.backlogtracker.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.backlogtracker.config.domain.ConfigHistory;
import com.backlogtracker.backlogtracker.config.dto.UpdateConfigRequest;
import com.backlogtracker.backlogtracker.config.repository.ConfigHistoryRepository;
import com.backlogtracker.backlogtracker.config.repository.ConfigRepository;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.service.GroupService;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read, seed, and edit the single {@link AppConfig} (design §7). Every change is written
 * to {@code configHistory} (§15). Priority list edits follow the §2 safety rules: more
 * than one item uses the value → blocked; exactly one → reassignment required; none →
 * removed outright. Category editing follows the same rules but lives on
 * {@code GroupCategoryService} instead — categories are per-group, not part of this
 * shared config.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository repository;
    private final ConfigHistoryRepository history;
    private final ItemRepository items;
    private final GroupService groupService;
    private final MongoOperations mongo;
    private final Clock clock;

    public AppConfig getConfig() {
        AppConfig cfg = repository.findById(AppConfig.SINGLETON_ID)
                .orElseGet(() -> repository.save(AppConfig.defaults()));
        // joined in from the platform-wide cap, not persisted on this document — see
        // AppConfig.maxGroupsPerUser's javadoc.
        cfg.setMaxGroupsPerUser(groupService.maxGroupsPerApplet(Group.APPLET_BACKLOG_TRACKER));
        return cfg;
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
        groupService.setMaxGroupsPerApplet(Group.APPLET_BACKLOG_TRACKER, r.maxGroupsPerUser());
        saved.setMaxGroupsPerUser(r.maxGroupsPerUser());
        record(actorId, "weights & thresholds updated", before, snapshot(saved));
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
            throw conflict(n, used);
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

    private ResponseStatusException conflict(String name, long used) {
        List<String> titles = items.findAll().stream()
                .filter(i -> name.equals(i.getPriority()))
                .map(Item::getTitle)
                .limit(8)
                .toList();
        return new ResponseStatusException(HttpStatus.CONFLICT,
                used + " items use the priority '" + name + "' — reassign them first: " + titles);
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
        m.put("maxGroupsPerUser", c.getMaxGroupsPerUser());
        m.put("buriedPriorityLevels", new ArrayList<>(c.getBuriedPriorityLevels()));
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
