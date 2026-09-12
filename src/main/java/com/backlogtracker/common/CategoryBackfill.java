package com.backlogtracker.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.backlogtracker.group.domain.Group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time backfill for the move from a single shared category list (on the old
 * singleton {@code AppConfig}) to one list per group (design: categories are per-group,
 * not shared config — see {@link Group#getCategories()}). Any group predating that
 * change has no {@code categories} field at all; give it the same starting list new
 * groups get, so its Settings screen isn't empty. Runs after {@link GroupBackfill}, which
 * is what actually creates groups for a pre-groups database.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CategoryBackfill implements ApplicationRunner {

    private final MongoOperations mongo;

    @Override
    public void run(ApplicationArguments args) {
        Query missing = new Query(new Criteria().orOperator(
                Criteria.where("categories").exists(false),
                Criteria.where("categories").size(0)));
        long updated = mongo.updateMulti(missing,
                Update.update("categories", Group.defaultCategories()), Group.class)
                .getModifiedCount();
        if (updated > 0) {
            log.info("CategoryBackfill: seeded default categories on {} pre-existing group(s)", updated);
        }
    }
}
