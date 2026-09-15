package com.backlogtracker.materialinventory.transfer.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A targeted ask for yarn from one specific member to another (design decision — not a
 * broadcast to the whole business). {@code fulfilledQuantity} accumulates as the target
 * hands over yarn in one or more installments (design decision: allow partial
 * fulfillment); reaching {@code requestedQuantity} does not auto-complete the request —
 * only the target member may mark it {@code COMPLETED} (design decision: only the
 * receiver can mark it complete), whether or not it was ever fully fulfilled.
 */
@Document("materialInventoryTransferRequests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @Id
    private String id;

    @Indexed
    private String groupId;

    /** Who's asking for yarn. */
    private String requesterId;
    /** Who has it — the only person who may fulfill or complete this request. */
    private String targetUserId;

    private String yarnTypeId;
    private double requestedQuantity;
    @Builder.Default
    private double fulfilledQuantity = 0;

    private TransferStatus status;

    private Instant createdAt;
    private Instant resolvedAt;
}
