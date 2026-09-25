package com.backlogtracker.materialinventory.transfer.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A targeted ask for yarn from one specific member to another (design decision — not a
 * broadcast to the whole business), now covering one or more yarn types in a single ask
 * (each its own {@link TransferLine}) and modeling the real physical hand-off as two
 * separate steps instead of one: the target <b>sends</b> a {@link Shipment} (debits their
 * own on-hand immediately, same as before), and only once the requester <b>confirms</b>
 * that specific shipment arrived does it credit the requester's on-hand — the yarn is in
 * neither party's inventory while it's genuinely in transit. This replaces the earlier
 * single debit-and-credit-at-once {@code fulfill()} step.
 *
 * <p>There's deliberately no single status for the request as a whole any more — each line
 * tracks its own {@link LineStatus} independently, since a real request can have one yarn
 * type fully received while another is still open, sent-but-unconfirmed, or the requester
 * has simply decided they don't need any more of it from this particular target (closing a
 * line never touches a shipment already sent — see {@link LineStatus#CLOSED}).
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
    /** Who has it — the only person who may send against any line of this request. */
    private String targetUserId;

    @Builder.Default
    private List<TransferLine> lines = new ArrayList<>();

    private Instant createdAt;

    /** Guards every read-modify-write on this document (appending a shipment, confirming
     *  one received, closing a line) — two concurrent mutations of the same request retry
     *  on a lost-update race (mapped to 409 by {@code GlobalExceptionHandler}) instead of
     *  one silently overwriting the other. */
    @Version
    private Long version;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferLine {
        private String lineId;
        private String yarnTypeId;
        private double requestedQuantity;
        private LineStatus status;
        @Builder.Default
        private List<Shipment> shipments = new ArrayList<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Shipment {
        private String shipmentId;
        private double quantity;
        /** e.g. "sent via Blue Dart AWB123" or "handed to Priya in person" — the target's
         *  own note on how this particular shipment is making its way over. */
        private String notes;
        private Instant sentAt;
        /** Null until the requester confirms this specific shipment physically arrived —
         *  that confirmation is the moment its quantity credits the requester's inventory. */
        private Instant receivedAt;
    }
}
