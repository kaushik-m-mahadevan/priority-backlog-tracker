package com.backlogtracker.ordertracker.order.dto;

/** completionFraction in [0.0, 1.0]. */
public record UpdateStageRequest(double completionFraction) {
}
