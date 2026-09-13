package com.backlogtracker.ordertracker.order.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.counter.CounterService;

import lombok.RequiredArgsConstructor;

/**
 * 14-digit order numbers: [Location:3][Creator:3][OrderType:2][Sequence:6] (design spec —
 * the mockup's 12-digit/4-digit-sequence examples are stale, per
 * docs/design-platform-integration.md). The sequence is atomic and scoped per
 * (group, orderType) so individual and bulk orders in the same business never collide.
 */
@Service
@RequiredArgsConstructor
public class OrderNumberService {

    private final CounterService counterService;

    public String next(String groupId, String locationCode, String creatorCode, String orderTypeCode) {
        long seq = counterService.next("ordertracker-order-" + groupId + "-" + orderTypeCode);
        return pad(locationCode, 3) + pad(creatorCode, 3) + pad(orderTypeCode, 2) + "%06d".formatted(seq);
    }

    private String pad(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return "0".repeat(width - v.length()) + v;
    }
}
