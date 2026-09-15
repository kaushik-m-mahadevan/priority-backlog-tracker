package com.backlogtracker.ordertracker.order.dto;

import java.time.Instant;
import java.util.List;

import com.backlogtracker.ordertracker.order.domain.Order;

public record OrderFinalizationView(Order.FinalizationStatus status, double finalCost, double finalRevenue,
                                    double finalProfit, Instant finalizedAt, String proposedByUserId,
                                    List<String> approvedByUserIds, List<String> groupMemberIds) {
}
