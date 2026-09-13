package com.backlogtracker.commons.migration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.backlogtracker.commons.group.domain.Group;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time backfill for the move to groups: if no group exists yet, create
 * "&lt;admin&gt;'s Backlog" and drop every pre-groups item into it. Runs after
 * {@link LegacyDataMigration}; skips cleanly on a fresh database (no admin yet — the
 * demo seeder builds its own group).
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class GroupBackfill implements ApplicationRunner {

    private final GroupRepository groups;
    private final UserRepository users;
    private final MongoOperations mongo;

    @Override
    public void run(ApplicationArguments args) {
        if (groups.count() > 0) {
            return;
        }
        List<User> admins = users.findByRole(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        // Nothing pre-groups to rehome → don't fabricate a group. A reset/empty prod
        // database lands here: the admin signs in group-less and creates their own.
        long orphans = mongo.count(orphanQuery(), "items")
                + mongo.count(orphanQuery(), "archivedItems");
        if (orphans == 0) {
            return;
        }
        User admin = admins.get(0);
        Group g = groups.save(Group.builder()
                .name(admin.getName() + "'s Backlog")
                .createdByUserId(admin.getId())
                .memberIds(new ArrayList<>(List.of(admin.getId())))
                .build());

        long items = assignGroup("items", g.getId());
        long archived = assignGroup("archivedItems", g.getId());
        log.info("GroupBackfill: created '{}' ({}); assigned {} live + {} archived items",
                g.getName(), g.getId(), items, archived);
    }

    private long assignGroup(String collection, String groupId) {
        return mongo.updateMulti(orphanQuery(),
                new Update().set("groupId", groupId), collection).getModifiedCount();
    }

    private static Query orphanQuery() {
        return new Query(Criteria.where("groupId").exists(false));
    }
}
