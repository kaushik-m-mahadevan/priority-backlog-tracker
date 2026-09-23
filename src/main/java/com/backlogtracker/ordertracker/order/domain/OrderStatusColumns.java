package com.backlogtracker.ordertracker.order.domain;

/**
 * ad-3: the Kanban board groups the 7 order statuses into 4 columns — Pending, In
 * Progress, Completed, Closed — and the transition rule is column-based, not
 * status-based: moving within a column is always free, moving to the immediately
 * adjacent column is allowed (forward freely, backward with a justification), and a
 * multi-column jump is never allowed at all, even with a reason. {@code CANCELLED} sits
 * outside the board entirely — it's a separate, always-available action, not a column —
 * so it has no column index here and must never reach {@link #columnIndex}.
 */
public final class OrderStatusColumns {

    private OrderStatusColumns() {
    }

    public static final int PENDING = 0;
    public static final int IN_PROGRESS = 1;
    public static final int COMPLETED = 2;
    public static final int CLOSED = 3;

    public static int columnIndex(OrderStatus status) {
        return switch (status) {
            case INQUIRY, CONFIRMED -> PENDING;
            case IN_PROGRESS -> IN_PROGRESS;
            case READY_TO_SHIP, SHIPPED -> COMPLETED;
            case DELIVERED -> CLOSED;
            case CANCELLED -> throw new IllegalArgumentException(
                    "CANCELLED has no board column — it's a separate action, not a status move");
        };
    }
}
