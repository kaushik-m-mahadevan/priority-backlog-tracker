package com.backlogtracker.materialinventory.transfer.dto;

import java.time.Instant;

import com.backlogtracker.materialinventory.transfer.domain.TransferRequest;
import com.backlogtracker.materialinventory.transfer.domain.TransferStatus;

public record TransferRequestView(String id, String requesterId, String targetUserId, String yarnTypeId,
                                  double requestedQuantity, double fulfilledQuantity, TransferStatus status,
                                  Instant createdAt, Instant resolvedAt) {

    public static TransferRequestView of(TransferRequest r) {
        return new TransferRequestView(r.getId(), r.getRequesterId(), r.getTargetUserId(), r.getYarnTypeId(),
                r.getRequestedQuantity(), r.getFulfilledQuantity(), r.getStatus(), r.getCreatedAt(), r.getResolvedAt());
    }
}
