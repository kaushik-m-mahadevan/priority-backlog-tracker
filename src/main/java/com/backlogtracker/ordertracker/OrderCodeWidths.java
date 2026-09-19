package com.backlogtracker.ordertracker;

/** The fixed digit widths baked into every order number's
 * {@code [Location][Creator][OrderType][Sequence]} layout — shared between where each code
 * is assigned ({@code CreatorService}, {@code LocationCodeService}, both in {@code
 * ordertracker.master.service}) and where they're padded back together
 * ({@code OrderNumberService}, in {@code ordertracker.order.service}). Previously three
 * independent literals with nothing tying them together — a width change in one place would
 * have silently truncated order numbers instead of failing loudly. */
public final class OrderCodeWidths {

    public static final int LOCATION_CODE_DIGITS = 3;
    public static final int CREATOR_CODE_DIGITS = 3;
    public static final int ORDER_TYPE_CODE_DIGITS = 2;

    private OrderCodeWidths() {
    }
}
