package com.backlogtracker.materialinventory.assignment.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignment;
import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignmentStatus;

public record MaterialAssignmentView(String id, String proposerId, String recipientId, String yarnTypeId,
                                     double quantity, MaterialAssignmentStatus status,
                                     Instant createdAt, Instant resolvedAt) {

    public static MaterialAssignmentView of(MaterialAssignment a) {
        return new MaterialAssignmentView(a.getId(), a.getProposerId(), a.getRecipientId(), a.getYarnTypeId(),
                a.getQuantity(), a.getStatus(), a.getCreatedAt(), a.getResolvedAt());
    }
}
