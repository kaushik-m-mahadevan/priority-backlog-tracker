package com.backlogtracker.commons.link.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
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
 * A 1:1 link between two groups belonging to two different applets — e.g. an Order
 * Tracker business linked to its own separate Finance Tracker group, kept as a genuinely
 * separate permission scope on purpose (design: platform integration). Generalizes the
 * one-off pattern Order Tracker already used for linking a single order to a Backlog
 * Tracker item ({@code Item.linkedOrderId}) to any pair of applets, any number of times,
 * without a bespoke field per pairing.
 *
 * <p>{@code A}/{@code B} is a stored convention, not a meaningful ordering — by
 * convention the "primary"/initiating group (so far always the Order Tracker business)
 * is {@code A} and the group it links to is {@code B}, which keeps every call site
 * consistent, but nothing about the schema requires that direction.
 *
 * <p>The two compound unique indexes below are what actually enforce "1:1 per applet
 * pairing": group A can have at most one linked group of a given other appletKey, and
 * group B can have at most one linked group of a given other appletKey — independently
 * enforced so a business can hold one Finance link AND one Inventory link AND one
 * Catalog link at the same time without those being the same kind of pairing.
 */
@Document("groupLinks")
@CompoundIndexes({
        @CompoundIndex(name = "groupA_appletB_unique", def = "{'groupIdA': 1, 'appletKeyB': 1}", unique = true),
        @CompoundIndex(name = "groupB_appletA_unique", def = "{'groupIdB': 1, 'appletKeyA': 1}", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupLink {

    @Id
    private String id;

    private String groupIdA;
    private String appletKeyA;
    private String groupIdB;
    private String appletKeyB;

    @CreatedDate
    private Instant createdAt;
}
