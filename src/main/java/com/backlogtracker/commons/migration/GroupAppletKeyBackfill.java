package com.backlogtracker.commons.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every group created before Order Tracker existed has no {@code appletKey} field at all
 * (the field was added in the platform-integration Phase 2 restructure, after those
 * documents were written). {@code GroupService.myGroups} now filters by an exact
 * {@code appletKey} match, so an un-backfilled group silently vanishes from every group
 * switcher — this repairs that in place, once, by treating "missing" as Backlog Tracker's
 * key, since that was the only applet in existence when those groups were created.
 *
 * <p>Unlike {@link GroupBackfill} this must run even when groups already exist — it is
 * exactly the group-count-greater-than-zero case that needs the fix — so it does not share
 * that early-return guard.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class GroupAppletKeyBackfill implements ApplicationRunner {

    private final MongoOperations mongo;

    @Override
    public void run(ApplicationArguments args) {
        long updated = mongo.updateMulti(
                Query.query(Criteria.where("appletKey").exists(false)),
                new Update().set("appletKey", Group.APPLET_BACKLOG_TRACKER),
                Group.class).getModifiedCount();
        if (updated > 0) {
            log.info("GroupAppletKeyBackfill: set appletKey={} on {} pre-existing group(s)",
                    Group.APPLET_BACKLOG_TRACKER, updated);
        }
    }
}
