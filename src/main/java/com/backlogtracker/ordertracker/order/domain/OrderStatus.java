package com.backlogtracker.ordertracker.order.domain;

/** Spec §5.1. Free-form assignment — any status can move to any other, matching the
 *  existing ItemStatus precedent of no guarded state machine (platform integration
 *  decision) — the sequence below is the expected happy path, not an enforced one. */
public enum OrderStatus {
    INQUIRY, CONFIRMED, IN_PROGRESS, READY_TO_SHIP, SHIPPED, DELIVERED, CANCELLED
}
