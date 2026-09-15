package com.backlogtracker.commons.approval.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generic "every current group member must unanimously approve before this takes
 * effect" workflow — extracted from Order Tracker's own cost-config change-request
 * (which pioneered exactly this pattern: propose, unanimous approve, single rejection
 * cancels it, a member leaving mid-vote invalidates it, optimistic-locking-safe
 * concurrent approval) so a second and third use (order-finalization, profit-split
 * consensus — design decision) don't each reimplement the same state machine.
 *
 * <p>{@code kind} identifies which feature owns this request (e.g.
 * {@code "ordertracker:cost-config"}, or {@code "ordertracker:order-finalization:<orderId>"}
 * for a per-order approval) — the single-pending-request guard is scoped to
 * {@code (groupId, kind)}, not just {@code groupId}, so unrelated approval workflows in
 * the same group never block each other.
 *
 * <p>{@code payload} is opaque here on purpose: this layer only runs the approval
 * mechanics, never interprets what's being approved. The service method that proposes a
 * request is responsible for reading {@code payload} back out and applying whatever
 * domain-specific effect approval should trigger, exactly once, after a save actually
 * succeeds (see {@link com.backlogtracker.commons.approval.service.ApprovalService} for
 * why that ordering matters under retry).
 */
@Document("approvalRequests")
@CompoundIndexes({
        @CompoundIndex(name = "group_kind_status", def = "{'groupId': 1, 'kind': 1, 'status': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    private String id;

    private String groupId;
    private String kind;

    @Builder.Default
    private Map<String, Object> payload = Map.of();

    private String proposedByUserId;
    @Builder.Default
    private List<String> approvedByUserIds = new ArrayList<>();

    private ApprovalStatus status;
    private String rejectedByUserId;

    private Instant createdAt;
    private Instant resolvedAt;

    /** Guards against a lost-update race when two members approve concurrently. */
    @Version
    private Long version;
}
