package com.backlogtracker.ordertracker.order.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.backlogtracker.commons.finance.OrderSplitLookup;
import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.ordertracker.master.domain.Creator;
import com.backlogtracker.ordertracker.master.service.CreatorService;
import com.backlogtracker.ordertracker.order.domain.Order;
import com.backlogtracker.ordertracker.order.domain.OrderType;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/** mb-18: Order Tracker's side of {@link OrderSplitLookup} — resolves an order by its own
 *  order number and aggregates its real per-creator split allocation, translating each
 *  {@code Creator.id} to the platform user id Finance Tracker's {@code personId} expects. */
@Component
@RequiredArgsConstructor
public class OrderTrackerSplitLookup implements OrderSplitLookup {

    private final OrderRepository repository;
    private final CreatorService creatorService;

    @Override
    public String appletKey() {
        return Group.APPLET_ORDER_TRACKER;
    }

    @Override
    public Optional<OrderSplitView> findByReference(String groupId, String userId, String reference) {
        return repository.findByGroupId(groupId).stream()
                .filter(o -> reference.equals(o.getOrderNumber()))
                .findFirst()
                .map(order -> buildView(groupId, order));
    }

    private OrderSplitView buildView(String groupId, Order order) {
        Map<String, Integer> unitsByCreatorId = new LinkedHashMap<>();
        if (order.getOrderType() == OrderType.BULK && order.getBulkDetails() != null) {
            for (Order.Variant variant : order.getBulkDetails().getVariants()) {
                for (Order.SplitLine split : variant.getSplitAllocation()) {
                    if (split.getCreatorId() == null || split.getCreatorId().isBlank()) {
                        continue;
                    }
                    unitsByCreatorId.merge(split.getCreatorId(), split.getQuantityAssigned(), Integer::sum);
                }
            }
        } else if (order.getCreatedByCreatorId() != null) {
            unitsByCreatorId.merge(order.getCreatedByCreatorId(), 1, Integer::sum);
        }
        List<RecipientSplit> recipients = unitsByCreatorId.entrySet().stream()
                .map(e -> {
                    Creator creator = creatorService.requireById(groupId, e.getKey());
                    return new RecipientSplit(creator.getUserId(), e.getValue());
                })
                .toList();
        return new OrderSplitView(order.getOrderNumber(), recipients);
    }
}
