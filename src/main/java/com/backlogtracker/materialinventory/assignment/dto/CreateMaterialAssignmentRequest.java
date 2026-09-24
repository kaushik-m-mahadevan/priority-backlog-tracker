package com.backlogtracker.materialinventory.assignment.dto;

public record CreateMaterialAssignmentRequest(String recipientId, String yarnTypeId, double quantity) {
}
