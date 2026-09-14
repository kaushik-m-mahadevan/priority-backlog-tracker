package com.backlogtracker.commons.user;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.backlogtracker.commons.user.service.UserService;

/** Regression test for the same bug class as the isTool/splitTracked outages, one layer
 *  down: User.handle's unique index used to be {@code sparse}, which doesn't exclude an
 *  explicit {@code null} written by a full-document resave of a legacy (pre-handle) user —
 *  only a genuinely-absent key. A second such resave would 11000 on {handle: null}. */
@SpringBootTest
class UserHandleLegacyDataTest {

    @Autowired MongoOperations mongo;
    @Autowired UserService userService;

    @Test
    void resavingTwoLegacyUsersWithNoHandleDoesNotCollide() {
        String idA = "legacy-handle-test-user-a";
        String idB = "legacy-handle-test-user-b";
        mongo.getCollection("users").insertOne(new Document("_id", idA)
                .append("name", "Legacy A").append("email", "legacy-a@x.test")
                .append("passwordHash", "x").append("role", "USER").append("status", "ACTIVE"));
        mongo.getCollection("users").insertOne(new Document("_id", idB)
                .append("name", "Legacy B").append("email", "legacy-b@x.test")
                .append("passwordHash", "x").append("role", "USER").append("status", "ACTIVE"));

        // Each save is a full-document resave of an entity whose in-memory `handle` is
        // Java null (never set) — exactly what UserService.approve/updateProfile do for
        // any account that predates the handle field. Two of these used to be the
        // reproduction for the E11000 on {handle: null}.
        assertThatCode(() -> {
            userService.updateProfile(idA, "Legacy A Renamed");
            userService.updateProfile(idB, "Legacy B Renamed");
        }).doesNotThrowAnyException();

        mongo.remove(new Query(Criteria.where("_id").in(idA, idB)), "users");
    }
}
