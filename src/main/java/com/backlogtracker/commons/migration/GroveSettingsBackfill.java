package com.backlogtracker.commons.migration;

import java.util.List;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time backfill for the move of {@code animationsEnabled} off the shared {@code User}
 * document (a platform-wide "commons" entity) onto Backlog Tracker's own
 * {@code groveSettings} collection (design: platform integration — see
 * {@link com.backlogtracker.backlogtracker.insights.domain.GroveSettings}). The Java model no longer has
 * the field, but existing Mongo documents still carry it as raw data until this runs once;
 * reads it directly via {@code MongoOperations} rather than through the (now field-less)
 * {@code User} class.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class GroveSettingsBackfill implements ApplicationRunner {

    private final MongoOperations mongo;

    @Override
    public void run(ApplicationArguments args) {
        if (mongo.getCollection("groveSettings").estimatedDocumentCount() > 0) {
            return;
        }
        List<Document> withLegacyField = mongo.find(
                Query.query(Criteria.where("animationsEnabled").exists(true)),
                Document.class, "users");
        int migrated = 0;
        for (Document u : withLegacyField) {
            boolean enabled = Boolean.TRUE.equals(u.getBoolean("animationsEnabled"));
            Document settings = new Document("_id", u.get("_id").toString())
                    .append("animationsEnabled", enabled);
            mongo.getCollection("groveSettings").insertOne(settings);
            migrated++;
        }
        if (migrated > 0) {
            log.info("GroveSettingsBackfill: migrated {} user(s) off User.animationsEnabled", migrated);
        }
    }
}
