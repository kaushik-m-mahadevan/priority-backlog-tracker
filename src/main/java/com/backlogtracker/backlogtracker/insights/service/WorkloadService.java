package com.backlogtracker.backlogtracker.insights.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backlogtracker.backlogtracker.config.domain.AppConfig;
import com.backlogtracker.backlogtracker.config.service.ConfigService;
import com.backlogtracker.backlogtracker.insights.dto.OwnerWorkloadView;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.service.ItemService;
import com.backlogtracker.commons.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Owner Workload Overview (design §6): open item count, category breakdown, and
 * "hot" (highest-weighted) priority count per owner. Items with no owner go in an
 * explicit "Unassigned" bucket rather than being dropped.
 */
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private static final String UNASSIGNED = "__unassigned__";

    private final ItemService itemService;
    private final UserRepository users;
    private final ConfigService configService;

    public OwnerWorkloadView.Overview overview(String groupId) {
        Map<String, String> nameById = users.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getName(), (a, b) -> a));

        AppConfig cfg = configService.getConfig();
        Set<String> hotPriorities = hotPriorities(cfg);

        Map<String, List<Item>> byOwner = itemService.listLive(groupId).stream()
                .collect(Collectors.groupingBy(
                        i -> i.getOwnerId() == null || i.getOwnerId().isBlank()
                                ? UNASSIGNED : i.getOwnerId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<OwnerWorkloadView> rows = byOwner.entrySet().stream()
                .map(e -> toRow(e.getKey(), e.getValue(), nameById, hotPriorities))
                .sorted(Comparator
                        .comparing(OwnerWorkloadView::ownerName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new OwnerWorkloadView.Overview(rows);
    }

    /** "Hot" = the two highest-weighted priority tiers configured right now (Critical/High
     *  by default), derived from {@code priorityValues} rather than hardcoded names — so
     *  renaming a priority, or reordering the weights, can't silently zero this count out. */
    private static Set<String> hotPriorities(AppConfig cfg) {
        return cfg.getPriorityValues().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private OwnerWorkloadView toRow(String ownerKey, List<Item> items, Map<String, String> nameById,
                                    Set<String> hotPriorities) {
        boolean unassigned = UNASSIGNED.equals(ownerKey);
        String ownerId = unassigned ? null : ownerKey;
        String ownerName = unassigned ? "Unassigned"
                : nameById.getOrDefault(ownerKey, "(unknown)");

        Map<String, Long> byCategory = items.stream()
                .collect(Collectors.groupingBy(Item::getCategory, TreeMap::new, Collectors.counting()));

        long criticalHigh = items.stream()
                .filter(i -> hotPriorities.contains(i.getPriority()))
                .count();

        return new OwnerWorkloadView(ownerId, ownerName, items.size(), byCategory, criticalHigh);
    }
}
