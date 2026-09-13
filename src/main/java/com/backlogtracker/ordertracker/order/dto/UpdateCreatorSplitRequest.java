package com.backlogtracker.ordertracker.order.dto;

/** completionFraction in [0.0, 1.0] — this creator's own share of the batch. */
public record UpdateCreatorSplitRequest(double completionFraction) {
}
