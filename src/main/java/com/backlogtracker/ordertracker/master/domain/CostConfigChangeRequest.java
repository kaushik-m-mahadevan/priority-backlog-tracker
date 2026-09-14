package com.backlogtracker.ordertracker.master.domain;

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
 * A proposed change to a business's overhead %/profit margin %, gated behind unanimous
 * member approval (mirrors Backlog Tracker's own unanimous archive-approval pattern,
 * platform integration decision) — every other BusinessConfig field stays freely editable
 * by any member; only these two, since they directly change every order's price.
 */
@Document("orderTrackerCostConfigChangeRequests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostConfigChangeRequest {

    @Id
    private String id;

    @Indexed
    private String groupId;

    private double proposedOverheadPercentage;
    private double proposedProfitMarginPercentage;

    private String proposedByUserId;
    @Builder.Default
    private List<String> approvedByUserIds = new ArrayList<>();

    private CostConfigChangeStatus status;
    private String rejectedByUserId;

    private Instant createdAt;
    private Instant resolvedAt;

    /** Guards against a lost-update race when two members approve concurrently — without
     *  this, the second save silently overwrites the first's approvedByUserIds entry. */
    @Version
    private Long version;
}
