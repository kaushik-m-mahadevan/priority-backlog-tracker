package com.backlogtracker.ordertracker.order.dto;

/** ad-3: the guided cancel flow — {@code reason} is required (a canned category or free
 *  text from the frontend's "Other"); {@code note} is optional extra detail. Refunding, if
 *  any, is a separate step through the ordinary payment-recording endpoint (type =
 *  REFUND) — not part of this request. */
public record CancelOrderRequest(String reason, String note) {
}
