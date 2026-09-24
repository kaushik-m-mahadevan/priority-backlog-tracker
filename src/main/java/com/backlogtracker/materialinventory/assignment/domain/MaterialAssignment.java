package com.backlogtracker.materialinventory.assignment.domain;

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
 * mb-21: a proposed hand-off of yarn from one specific member to another, gated by a
 * propose/accept handshake rather than a direct set — chosen specifically for the audit
 * trail (design decision, mirrors the two-named-parties shape of {@code TransferRequest}
 * but runs in the opposite direction: here the *holder* proposes, not the recipient
 * asking). {@code quantity} is debited from {@code proposerId}'s own on-hand the moment
 * this is proposed (e.g. "I'm shipping 5 skeins to Bangalore" — it's no longer really
 * theirs from that point on) and only credited to {@code recipientId} once accepted; a
 * reject/cancel credits it straight back to the proposer.
 */
@Document("materialInventoryAssignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialAssignment {

    @Id
    private String id;

    @Indexed
    private String groupId;

    /** Who's sending the yarn — already debited the moment this is proposed. */
    private String proposerId;
    /** Who must accept before it lands on their own on-hand row. */
    private String recipientId;

    private String yarnTypeId;
    private double quantity;

    private MaterialAssignmentStatus status;

    private Instant createdAt;
    private Instant resolvedAt;
}
