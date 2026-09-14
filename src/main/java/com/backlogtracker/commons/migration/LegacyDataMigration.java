package com.backlogtracker.commons.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.backlogtracker.backlogtracker.config.domain.AppConfig;

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

        // drop the old unique index FIRST — renaming userCode away would otherwise leave
        // every document with userCode:null and blow up the (non-sparse) unique index.
        try {
            mongo.indexOps("users").dropIndex("userCode");
        } catch (RuntimeException ignored) {
            // never existed (fresh DB) — fine
        }
        long renamed = mongo.updateMulti(
                new Query(Criteria.where("userCode").exists(true)),
                new Update().rename("userCode", "handle"), "users").getModifiedCount();

        // superseded by the "handle_unique_partial" partial-filter index declared on User
        // now (the old sparse one has the same null-collision bug linkedOrderId had) — the
        // new index is created fresh under its own name at startup regardless of this, so
        // dropping the old one here (after, not blocking, that) is just cleanup.
        try {
            mongo.indexOps("users").dropIndex("handle");
        } catch (RuntimeException ignored) {
            // already dropped, or never existed (fresh DB) — fine
        }

        long capped = mongo.updateFirst(
                new Query(Criteria.where("_id").is(AppConfig.SINGLETON_ID)
                        .and("maxGroupsPerUser").exists(false)),
                new Update().set("maxGroupsPerUser", 5), "config").getModifiedCount();

        // drop the vestigial per-item scope field
        mongo.updateMulti(new Query(Criteria.where("scope").exists(true)),
                new Update().unset("scope"), "items");
        mongo.updateMulti(new Query(Criteria.where("scope").exists(true)),
                new Update().unset("scope"), "archivedItems");

        // Order Tracker's rebuild (commit 54c7b29) renamed OrderStatus/PaymentStatus values;
        // a pre-rebuild order document still carrying an old value 400s on read with
        // "No enum constant ...OrderStatus.RECEIVED" the same way the OWNER/CONTRIBUTOR
        // roles above did before this migration existed.
        long ordersReceived = mongo.updateMulti(
                new Query(Criteria.where("status").is("RECEIVED")),
                new Update().set("status", "INQUIRY"), "orderTrackerOrders").getModifiedCount();
        long ordersReady = mongo.updateMulti(
                new Query(Criteria.where("status").is("READY_FOR_SHIPMENT")),
                new Update().set("status", "READY_TO_SHIP"), "orderTrackerOrders").getModifiedCount();
        long paymentsPaid = mongo.updateMulti(
                new Query(Criteria.where("paymentStatus").is("PAID")),
                new Update().set("paymentStatus", "PAID_IN_FULL"), "orderTrackerOrders").getModifiedCount();
        long paymentsRefunded = mongo.updateMulti(
                new Query(Criteria.where("paymentStatus").is("FULLY_REFUNDED")),
                new Update().set("paymentStatus", "REFUNDED"), "orderTrackerOrders").getModifiedCount();

        if (admins + users + activated + renamed + capped > 0) {
            log.info("LegacyDataMigration: OWNER->ADMIN x{}, CONTRIBUTOR/VIEWER->USER x{}, "
                    + "status->ACTIVE x{}, userCode->handle x{}, maxGroupsPerUser set x{}",
                    admins, users, activated, renamed, capped);
        }
        if (ordersReceived + ordersReady + paymentsPaid + paymentsRefunded > 0) {
            log.info("LegacyDataMigration: order status RECEIVED->INQUIRY x{}, "
                    + "READY_FOR_SHIPMENT->READY_TO_SHIP x{}, paymentStatus PAID->PAID_IN_FULL x{}, "
                    + "FULLY_REFUNDED->REFUNDED x{}",
                    ordersReceived, ordersReady, paymentsPaid, paymentsRefunded);
        }

        long totalUsers = mongo.count(new Query(), "users");
        if (totalUsers > 0 && mongo.count(new Query(Criteria.where("role").is("ADMIN")), "users") == 0) {
            log.warn("LegacyDataMigration: no ADMIN account exists ({} users) — approvals and "
                    + "formula settings are unavailable until one registers", totalUsers);
        }
    }
}
