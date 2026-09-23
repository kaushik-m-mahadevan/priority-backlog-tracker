package com.backlogtracker.ordertracker.order.dto;

import com.backlogtracker.ordertracker.order.domain.OrderStatus;

/** ad-3: column-based, strictly adjacent-only — see {@code OrderStatusColumns} and
 *  {@code OrderService.updateStatus}. {@code justification} is required (and only
 *  meaningful) for a backward column move; {@code status == CANCELLED} is always
 *  rejected here — that goes through the dedicated cancel endpoint instead. */
public record UpdateOrderStatusRequest(OrderStatus status, String justification) {
}
