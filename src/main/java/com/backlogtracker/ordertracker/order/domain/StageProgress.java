package com.backlogtracker.ordertracker.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One work stage's progress on this specific order, snapshotting
 * {@code BusinessConfig.WorkStageType} at order-creation time. {@code estimatedHours} is
 * this order's own estimate for the stage — it, not any global config, is what drives the
 * stage's share of the order's overall completion percentage (platform integration
 * decision: weighting is derived per-order from each stage's own hours, never a fixed
 * configured weight).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageProgress {

    private String stageKey;
    private String label;
    private int sequenceOrder;
    private String assigneeCreatorId;
    private double estimatedHours;
    /** 0.0 (not started) to 1.0 (done). */
    private double completionFraction;
}
