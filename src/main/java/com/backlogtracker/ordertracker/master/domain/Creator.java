package com.backlogtracker.ordertracker.master.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A group member's Order Tracker profile for that group's "business" (design §2 — the
 * people collection is generically Creator, not Founder: anyone can be assigned work).
 * One per (groupId, userId) — the same platform account can be a Creator in more than one
 * group, with different profile data in each, since each group is its own business
 * (platform integration decision). {@code hoursAvailablePerDay} is pulled from here
 * wherever a creator is assigned, never re-entered per order.
 */
@Document("orderTrackerCreators")
@CompoundIndexes({
        @CompoundIndex(name = "group_user", def = "{'groupId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "group_code", def = "{'groupId': 1, 'creatorCode': 1}", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Creator {

    @Id
    private String id;

    private String groupId;

    /** The commons User account this profile belongs to. */
    private String userId;

    private String name;
    private String baseLocation;
    private String locationCode;

    /** 3-digit, auto-assigned by the app — not manually chosen (design §5.1). */
    private String creatorCode;

    private double hoursAvailablePerDay;

    /** ad-2: whether this person's own yarn usage-log entries immediately decrement their
     *  real Material Inventory on-hand quantity (true) or accumulate as pending until they
     *  run the manual "sync to inventory" sweep (false, the default — the safer starting
     *  point, since auto-sync means every logged skein instantly changes a real count with
     *  no review step). Per-person, not business-wide, per spec. */
    private boolean autoSyncInventory;
}
