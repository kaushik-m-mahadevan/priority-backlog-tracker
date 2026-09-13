package com.backlogtracker.backlogtracker.archive.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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

    public enum Status { PENDING, APPROVED, REJECTED }

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

    public boolean hasApproval(String userId) {
        return approvedByUserIds.contains(userId);
    }
}
