package com.backlogtracker.ordertracker.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One creator's share of a bulk order's total quantity (design §9). The batch's
 * {@code computedDueDate} is driven by the slowest-loaded split — see
 * {@link OrderCalculator#computedBulkDueDate}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorSplit {

    private String creatorId;
    private int assignedQuantity;
    private double estimatedHoursPerUnit;
    /** 0.0 (not started) to 1.0 (this creator's whole split is done). */
    private double completionFraction;
}
