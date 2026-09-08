package com.backlogtracker.insights.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backlogtracker.insights.dto.OwnerWorkloadView;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.service.ItemService;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Owner Workload Overview (design §6): open item count, category breakdown, and
 * Critical/High count per owner. Items with no owner go in an explicit "Unassigned"
 * bucket rather than being dropped.
 */
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private static final String UNASSIGNED = "__unassigned__";
    private static final Set<String> HOT_PRIORITIES = Set.of("Critical", "High");

    private final ItemService itemService;
    private final UserRepository users;

    public OwnerWorkloadView.Overview overview() {
        Map<String, String> nameById = users.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getName(), (a, b) -> a));

        Map<String, List<Item>> byOwner = itemService.listShared().stream()
                .collect(Collectors.groupingBy(
                        i -> i.getOwnerId() == null || i.getOwnerId().isBlank()
                                ? UNASSIGNED : i.getOwnerId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<OwnerWorkloadView> rows = byOwner.entrySet().stream()
                .map(e -> toRow(e.getKey(), e.getValue(), nameById))
                .sorted(Comparator
                        .comparing(OwnerWorkloadView::ownerName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new OwnerWorkloadView.Overview(rows);
    }

    private OwnerWorkloadView toRow(String ownerKey, List<Item> items, Map<String, String> nameById) {
        boolean unassigned = UNASSIGNED.equals(ownerKey);
        String ownerId = unassigned ? null : ownerKey;
        String ownerName = unassigned ? "Unassigned"
                : nameById.getOrDefault(ownerKey, "(unknown)");

        Map<String, Long> byCategory = items.stream()
                .collect(Collectors.groupingBy(Item::getCategory, TreeMap::new, Collectors.counting()));

        long criticalHigh = items.stream()
                .filter(i -> HOT_PRIORITIES.contains(i.getPriority()))
                .count();

        return new OwnerWorkloadView(ownerId, ownerName, items.size(), byCategory, criticalHigh);
    }
}
