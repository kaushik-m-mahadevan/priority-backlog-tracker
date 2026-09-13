package com.backlogtracker.ordertracker.order.domain;

/** Free-form — any status can move to any other, matching the ItemStatus precedent of no
 *  guarded state machine (platform integration decision). */
public enum OrderStatus {
    RECEIVED, IN_PROGRESS, READY_FOR_SHIPMENT, SHIPPED, DELIVERED, CANCELLED
}
