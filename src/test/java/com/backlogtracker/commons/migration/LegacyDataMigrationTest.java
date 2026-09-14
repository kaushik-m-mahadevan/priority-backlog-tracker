package com.backlogtracker.commons.migration;

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
                .append("role", "OWNER").append("userCode", "lgo"));
        mongo.getCollection("users").insertOne(new Document("email", "legacy-viewer@x.test")
                .append("role", "VIEWER").append("userCode", "lgv"));

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

    @Test
    void rewritesLegacyOrderAndPaymentStatuses() {
        mongo.getCollection("orders").insertOne(new Document("orderNumber", "LEGACY-1")
                .append("status", "RECEIVED").append("paymentStatus", "PAID"));
        mongo.getCollection("orders").insertOne(new Document("orderNumber", "LEGACY-2")
                .append("status", "READY_FOR_SHIPMENT").append("paymentStatus", "FULLY_REFUNDED"));

        migration.run(null);

        Document o1 = mongo.getCollection("orders")
                .find(new Document("orderNumber", "LEGACY-1")).first();
        Document o2 = mongo.getCollection("orders")
                .find(new Document("orderNumber", "LEGACY-2")).first();

        assertThat(o1.getString("status")).isEqualTo("INQUIRY");
        assertThat(o1.getString("paymentStatus")).isEqualTo("PAID_IN_FULL");
        assertThat(o2.getString("status")).isEqualTo("READY_TO_SHIP");
        assertThat(o2.getString("paymentStatus")).isEqualTo("REFUNDED");

        mongo.remove(new Query(Criteria.where("orderNumber").regex("^LEGACY-")), "orders");
    }
}
