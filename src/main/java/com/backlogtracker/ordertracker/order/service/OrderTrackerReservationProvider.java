package com.backlogtracker.ordertracker.order.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.inventory.ReservationProvider;
import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.OrderStatus;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * ad-2's cross-applet {@link ReservationProvider} — computed live from open orders rather
 * than stored, so a reservation "releases" the moment an order is cancelled/delivered or
 * enough usage is logged against it, with no separate release step to remember. Individual
 * orders attribute their whole yarn need to whoever is assigned the crocheting stage
 * (falling back to whoever logged the order, if unassigned); bulk orders apportion each
 * variant's need across its {@code splitAllocation} by units assigned — {@code MandatoryItem
 * .quantity} on a variant is per-unit (same convention {@code OrderCalculator.priceVariant}
 * uses for cost), so a creator's share is per-unit need x their own assigned units, not a
 * fraction (spec's own example: 8 units split 5/3 reserves 5 and 3 skeins respectively, for
 * a 1-skein-per-unit yarn). Logged usage against the same yarn type on the same order reduces
 * the outstanding reservation, floored at zero.
 */
@Component
@RequiredArgsConstructor
public class OrderTrackerReservationProvider implements ReservationProvider {

    private final OrderRepository orderRepository;
    private final CreatorRepository creatorRepository;

    @Override
    public String appletKey() {
        return Group.APPLET_ORDER_TRACKER;
    }

    @Override
    public Map<String, Double> reservedQuantities(String groupId, String userId) {
        Creator creator = creatorRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (creator == null) {
            return Map.of();
        }
        String creatorId = creator.getId();
        Map<String, Double> totals = new HashMap<>();
        for (Order order : orderRepository.findByGroupId(groupId)) {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
                continue;
            }
            if (order.getOrderType() == OrderType.INDIVIDUAL) {
                if (creatorId.equals(craftingAssignee(order))) {
                    addYarn(totals, order.getMandatoryItems(), 1.0);
                    for (Order.Component c : order.getComponents()) {
                        addYarn(totals, c.getMandatoryItems(), 1.0);
                    }
                }
            } else if (order.getBulkDetails() != null) {
                for (Order.Variant variant : order.getBulkDetails().getVariants()) {
                    for (Order.SplitLine split : variant.getSplitAllocation()) {
                        if (!creatorId.equals(split.getCreatorId())) {
                            continue;
                        }
                        // MandatoryItem.quantity on a variant is per-unit (same convention
                        // OrderCalculator.priceVariant uses for cost) — this creator's share
                        // is per-unit need × the units actually assigned to them, not a
                        // fraction of the variant's total.
                        addYarn(totals, variant.getMandatoryItems(), split.getQuantityAssigned());
                        for (Order.Component c : variant.getComponents()) {
                            addYarn(totals, c.getMandatoryItems(), split.getQuantityAssigned());
                        }
                    }
                }
            }
            for (Order.UsageLogEntry usage : order.getUsageLogEntries()) {
                if (creatorId.equals(usage.getLoggedByCreatorId()) && usage.getYarnTypeId() != null) {
                    totals.merge(usage.getYarnTypeId(), -usage.getQuantity(), Double::sum);
                }
            }
        }
        totals.replaceAll((k, v) -> Math.max(0, v));
        totals.values().removeIf(v -> v <= 1e-9);
        return totals;
    }

    private static String craftingAssignee(Order order) {
        return order.getStageAssignments().stream()
                .filter(a -> BusinessConfig.STAGE_CROCHETING.equals(a.getStageKey()))
                .map(Order.StageAssignment::getAssignedCreatorId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(order.getCreatedByCreatorId());
    }

    private static void addYarn(Map<String, Double> totals, List<Order.MandatoryItem> items, double ratio) {
        for (Order.MandatoryItem item : items) {
            if (item.getKind() == Order.MaterialKind.YARN && item.getLinkedYarnTypeId() != null) {
                totals.merge(item.getLinkedYarnTypeId(), item.getQuantity() * ratio, Double::sum);
            }
        }
    }
}
