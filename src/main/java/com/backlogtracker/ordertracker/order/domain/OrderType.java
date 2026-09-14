package com.backlogtracker.ordertracker.order.domain;

/** Individual and bulk orders share one base schema (spec §5.1, design principle #4) —
 *  this discriminates which optional sub-document ({@code bulkDetails}) applies. */
public enum OrderType {
    INDIVIDUAL, BULK
}
