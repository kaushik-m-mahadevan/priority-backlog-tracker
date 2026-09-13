package com.backlogtracker.backlogtracker.insights.dto;

import java.util.List;
import java.util.Map;

/** One row of the Owner Workload Overview (design §6). */
public record OwnerWorkloadView(
        String ownerId,
        String ownerName,
        long openCount,
        Map<String, Long> byCategory,
        long criticalHighCount) {

    /** The whole overview: one row per owner, plus the explicit Unassigned bucket. */
    public record Overview(List<OwnerWorkloadView> owners) {
    }
}
