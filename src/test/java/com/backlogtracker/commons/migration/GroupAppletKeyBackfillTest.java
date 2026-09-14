package com.backlogtracker.commons.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.backlogtracker.commons.group.domain.Group;

/**
 * Reproduces the production bug: a group written before {@code appletKey} existed has no
 * such field at all, and {@code GroupService.myGroups}'s exact-match query silently drops
 * it. This proves the backfill repairs a document like that in place.
 */
@SpringBootTest
class GroupAppletKeyBackfillTest {

    @Autowired
    MongoOperations mongo;

    @Autowired
    GroupAppletKeyBackfill backfill;

    @Test
    void setsBacklogTrackerAppletKeyOnAGroupMissingTheField() {
        Document legacyGroup = new Document("name", "Pre-Phase-2 Group")
                .append("memberIds", java.util.List.of("someone"));
        mongo.insert(legacyGroup, "groups");
        String id = legacyGroup.getObjectId("_id").toHexString();

        backfill.run(null);

        Group reloaded = mongo.findOne(Query.query(Criteria.where("id").is(id)), Group.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getAppletKey()).isEqualTo(Group.APPLET_BACKLOG_TRACKER);

        mongo.remove(Query.query(Criteria.where("id").is(id)), Group.class);
    }

    @Test
    void leavesAnExistingAppletKeyUntouched() {
        Group orderTrackerGroup = mongo.save(Group.builder()
                .name("An Order Tracker business")
                .appletKey(Group.APPLET_ORDER_TRACKER)
                .memberIds(new java.util.ArrayList<>(java.util.List.of("someone")))
                .build());

        backfill.run(null);

        Group reloaded = mongo.findById(orderTrackerGroup.getId(), Group.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getAppletKey()).isEqualTo(Group.APPLET_ORDER_TRACKER);

        mongo.remove(Query.query(Criteria.where("id").is(orderTrackerGroup.getId())), Group.class);
    }
}
