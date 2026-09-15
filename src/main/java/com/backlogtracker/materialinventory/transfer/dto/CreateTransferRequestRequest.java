package com.backlogtracker.materialinventory.transfer.dto;

public record CreateTransferRequestRequest(String targetUserId, String yarnTypeId, double requestedQuantity) {
}
