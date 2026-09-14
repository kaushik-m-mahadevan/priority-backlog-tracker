package com.backlogtracker.backlogtracker.item.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A live backlog item — only {@code BACKLOG} or {@code IN_PROGRESS} (design §2, §24).
 * Terminal items live in {@code archivedItems} instead.
 *
 * <p>{@code editLock} and {@code archivalRequests} are added by later steps; MongoDB is
 * schemaless so their absence here is fine.
 */
@Document("items")
@CompoundIndexes({
        @CompoundIndex(name = "group_status", def = "{'groupId': 1, 'status': 1}"),
        @CompoundIndex(name = "group_owner", def = "{'groupId': 1, 'ownerId': 1}"),
        /* `sparse` only excludes documents where the key is entirely absent — Spring Data's
         * MongoConverter writes every field, including unset ones, as an explicit BSON null,
         * so a sparse index here still collides across every item with no link. A partial
         * filter that only indexes actual string values is the fix (caused a prod outage:
         * E11000 on {linkedOrderId: null} the first time this index tried to build). */
        @CompoundIndex(name = "group_linkedOrder", def = "{'groupId': 1, 'linkedOrderId': 1}", unique = true,
                partialFilter = "{'linkedOrderId': {'$type': 'string'}}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    @Id
    private String id;

    @Indexed(unique = true)
    private String itemId;

    private String title;
    private String category;
    private String priority;

    private EffortEstimate effortEstimate;

    private Instant dueDate;

    private ItemStatus status;

    /** The group this item belongs to. Every item is in exactly one group (design: groups). */
    private String groupId;

    /** Pinned items float to the top of the Pecking Order for everyone (design: pinning). */
    private boolean pinned;
    private Instant pinnedAt;
    private String pinnedByUserId;

    /** Audit-only — never gates edit rights (design §2, §8). */
    private String createdBy;
    private String lastUpdatedBy;

    /** Assignee, shown in Owner Workload (§6). Null == Unassigned. */
    private String ownerId;

    private Notes notes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Optimistic-locking version — a stale save is rejected with 409 (design §19). */
    @Version
    private Long version;

    /**
     * Opaque link to an Order Tracker order (platform integration follow-up) — at most one
     * item per group may point at a given order (the compound index above). This package
     * never interprets the value or imports anything from {@code ordertracker}; it's the
     * frontend that bridges the two applets by calling each one's own REST API. Deliberately
     * not surfaced anywhere in Backlog Tracker's own UI, only used for the linking check.
     */
    private String linkedOrderId;
}
