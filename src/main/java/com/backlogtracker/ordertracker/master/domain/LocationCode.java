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
 * One group's own location codes (design: Order Tracker §2). Per-group, not global —
 * every group is its own separate "business" (platform integration decision), so two
 * groups can each have their own "Bangalore" = a different 3-digit code, or the same one,
 * with no collision risk since they never share an order-number space.
 */
@Document("orderTrackerLocationCodes")
@CompoundIndexes({
        @CompoundIndex(name = "group_name", def = "{'groupId': 1, 'locationName': 1}", unique = true),
        @CompoundIndex(name = "group_code", def = "{'groupId': 1, 'code': 1}", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationCode {

    @Id
    private String id;

    private String groupId;
    private String locationName;

    /** 3-digit, auto-assigned by the app (generate-and-check against the unique index —
     *  design §5.1's feasibility note), never picked by hand. */
    private String code;
}
