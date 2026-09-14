package com.backlogtracker.ordertracker.order.dto;

/** unitsCompleted is 0 or 1 for an individual order (spec §5.11). assignedCreatorId may
 *  change independently of completion — logged to assigneeChangeHistory when it does. */
public record UpdateStageAssignmentRequest(String assignedCreatorId, int unitsCompleted) {
}
