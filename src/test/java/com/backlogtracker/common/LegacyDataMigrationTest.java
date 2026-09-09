package com.backlogtracker.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest
class LegacyDataMigrationTest {

    @Autowired LegacyDataMigration migration;
    @Autowired MongoOperations mongo;

    @Test
    void rewritesLegacyRolesAndFillsStatus() {
        mongo.getCollection("users").insertOne(new Document("email", "legacy-owner@x.test")
                .append("role", "OWNER").append("userCode", "LGO"));
        mongo.getCollection("users").insertOne(new Document("email", "legacy-viewer@x.test")
                .append("role", "VIEWER").append("userCode", "LGV"));

        migration.run(null);

        Document owner = mongo.getCollection("users")
                .find(new Document("email", "legacy-owner@x.test")).first();
        Document viewer = mongo.getCollection("users")
                .find(new Document("email", "legacy-viewer@x.test")).first();

        assertThat(owner.getString("role")).isEqualTo("ADMIN");
        assertThat(owner.getString("status")).isEqualTo("ACTIVE");
        assertThat(viewer.getString("role")).isEqualTo("USER");
        assertThat(viewer.getString("status")).isEqualTo("ACTIVE");

        mongo.remove(new Query(Criteria.where("email").regex("^legacy-")), "users");
    }
}
