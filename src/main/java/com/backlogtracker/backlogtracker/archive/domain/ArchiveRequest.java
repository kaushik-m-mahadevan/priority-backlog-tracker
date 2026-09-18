package com.backlogtracker.backlogtracker.archive.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
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
 * A request to archive an <em>active</em> item (design: archiving needs unanimous
 * group approval; {@code RESOLVED}/{@code REJECTED} do not). It is granted only when
 * every current group member has approved; a single rejection kills it.
 */
@Document("archiveRequests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRequest {

    public enum Status { PENDING, APPROVED, REJECTED, INVALIDATED }

    @Id
    private String id;

    @Indexed
    private String itemId;
    @Indexed
    private String groupId;
    private String itemTitle;

    private String requestedByUserId;
    private String requestedByName;
    private String note;

    private Status status;

    /** User ids that have approved. The requester is added on creation. */
    @Builder.Default
    private List<String> approvedByUserIds = new ArrayList<>();

    private String rejectedByUserId;
    private String rejectedByName;

    @CreatedDate
    private Instant createdAt;
    private Instant decidedAt;

    /** Guards the read-modify-write on {@code approvedByUserIds} — two members approving
     *  at once retry on a lost-update race instead of one silently overwriting the other's
     *  vote (same pattern as {@code CostConfigChangeRequest}/{@code TransferRequest}). */
    @Version
    private Long version;

    public boolean hasApproval(String userId) {
        return approvedByUserIds.contains(userId);
    }
}
