package com.backlogtracker.common;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.backlogtracker.config.domain.AppConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotent, forward-only data fixes for documents written by older versions. Runs as
 * raw Mongo updates on string values so the changed {@code Role} enum never has to
 * deserialize a legacy value ({@code OWNER}/{@code CONTRIBUTOR}/{@code VIEWER}). Ordered
 * first so it completes before anything reads a {@code User} document.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class LegacyDataMigration implements ApplicationRunner {

    private final MongoOperations mongo;

    @Override
    public void run(ApplicationArguments args) {
        long admins = mongo.updateMulti(
                new Query(Criteria.where("role").is("OWNER")),
                new Update().set("role", "ADMIN"), "users").getModifiedCount();

        long users = mongo.updateMulti(
                new Query(Criteria.where("role").in("CONTRIBUTOR", "VIEWER")),
                new Update().set("role", "USER"), "users").getModifiedCount();

        long activated = mongo.updateMulti(
                new Query(Criteria.where("status").exists(false)),
                new Update().set("status", "ACTIVE"), "users").getModifiedCount();

        long capped = mongo.updateFirst(
                new Query(Criteria.where("_id").is(AppConfig.SINGLETON_ID)
                        .and("maxGroupsPerUser").exists(false)),
                new Update().set("maxGroupsPerUser", 5), "config").getModifiedCount();

        if (admins + users + activated + capped > 0) {
            log.info("LegacyDataMigration: role OWNER->ADMIN x{}, CONTRIBUTOR/VIEWER->USER x{}, "
                    + "status->ACTIVE x{}, maxGroupsPerUser set x{}", admins, users, activated, capped);
        }

        List<?> ids = mongo.findDistinct(new Query(), "_id", "users", Object.class);
        if (mongo.count(new Query(Criteria.where("role").is("ADMIN")), "users") == 0 && !ids.isEmpty()) {
            log.warn("LegacyDataMigration: no ADMIN account exists ({} users) — approvals and "
                    + "formula settings are unavailable until one registers", ids.size());
        }
    }
}
