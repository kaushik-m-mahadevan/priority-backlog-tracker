package com.backlogtracker.ordertracker.order.dto;

import com.backlogtracker.ordertracker.order.domain.OrderStatus;

/** Free-form — any status may move to any other (platform integration decision). */
public record UpdateOrderStatusRequest(OrderStatus status) {
}
