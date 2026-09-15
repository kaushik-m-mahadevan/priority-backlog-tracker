package com.backlogtracker.commons.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.order.domain.Order.MandatoryItem;
import com.backlogtracker.ordertracker.order.domain.Order.ToolUsage;
import com.backlogtracker.ordertracker.order.domain.Order.Variant;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixes a defect where an order's materials/tools split was snapshotted at
 * create/edit time from whatever {@code MandatoryItemType.isTool} was <em>then</em> —
 * later flipping an existing item type's tool flag in Business Settings never
 * retroactively re-split orders that already existed, so (e.g.) a needle marked as a
 * tool would keep showing under Materials on every order created before the flag was
 * flipped. Idempotent: re-running finds nothing left to move once every order agrees
 * with its business's current type config. Runs after {@link LegacyDataMigration}
 * (unrelated fields, but keeps the "no enum crash first" priority intact).
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class OrderMaterialsToolsReconciliation implements ApplicationRunner {

    private final OrderRepository orderRepository;
    private final BusinessConfigRepository businessConfigRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("OrderMaterialsToolsReconciliation: starting");
        long ordersFixed = 0;
        for (BusinessConfig cfg : businessConfigRepository.findAll()) {
            Set<String> toolKeys = cfg.getMandatoryItemTypes().stream()
                    .filter(BusinessConfig.MandatoryItemType::isTool)
                    .map(BusinessConfig.MandatoryItemType::itemKey)
                    .collect(Collectors.toSet());
            var orders = orderRepository.findByGroupId(cfg.getGroupId());
            for (var order : orders) {
                boolean changed = reconcile(order.getMandatoryItems(), order.getTools(), toolKeys,
                        order::setMandatoryItems, order::setTools);
                if (order.getBulkDetails() != null) {
                    for (Variant v : order.getBulkDetails().getVariants()) {
                        changed |= reconcile(v.getMandatoryItems(), v.getTools(), toolKeys,
                                v::setMandatoryItems, v::setTools);
                    }
                }
                if (changed) {
                    orderRepository.save(order);
                    ordersFixed++;
                }
            }
        }
        if (ordersFixed > 0) {
            log.info("OrderMaterialsToolsReconciliation: re-split materials/tools on {} order(s)", ordersFixed);
        }
        log.info("OrderMaterialsToolsReconciliation: finished");
    }

    /** Moves any material entry whose itemKey is now a tool into the tools list (dropping
     *  quantity/unitCost, which tools don't carry), and any tool entry whose itemKey is now
     *  a material back into the materials list (with quantity 1 / unitCost 0 — the original
     *  values were never captured for a tool, so there's nothing truthful to restore; a
     *  business owner can correct them by hand if they matter). Returns whether either list
     *  changed. */
    private boolean reconcile(List<MandatoryItem> materials, List<ToolUsage> tools, Set<String> toolKeys,
                              java.util.function.Consumer<List<MandatoryItem>> setMaterials,
                              java.util.function.Consumer<List<ToolUsage>> setTools) {
        List<MandatoryItem> misclassifiedAsMaterial = materials.stream()
                .filter(m -> toolKeys.contains(m.getItemKey())).toList();
        List<ToolUsage> misclassifiedAsTool = tools.stream()
                .filter(t -> !toolKeys.contains(t.getItemKey())).toList();
        if (misclassifiedAsMaterial.isEmpty() && misclassifiedAsTool.isEmpty()) {
            return false;
        }

        List<MandatoryItem> newMaterials = new ArrayList<>(materials);
        newMaterials.removeAll(misclassifiedAsMaterial);
        misclassifiedAsTool.forEach(t -> newMaterials.add(MandatoryItem.builder()
                .itemKey(t.getItemKey()).value(t.getValue()).quantity(1).unitCost(0).notes(t.getNotes()).build()));

        List<ToolUsage> newTools = new ArrayList<>(tools);
        newTools.removeAll(misclassifiedAsTool);
        misclassifiedAsMaterial.forEach(m -> newTools.add(ToolUsage.builder()
                .itemKey(m.getItemKey()).value(m.getValue()).notes(m.getNotes()).build()));

        setMaterials.accept(newMaterials);
        setTools.accept(newTools);
        return true;
    }
}
