package com.backlogtracker.ordertracker.master;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.backlogtracker.ordertracker.master.domain.BusinessConfig;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;

/** Regression test for a prod outage: mandatoryItemTypes.isTool was added as a primitive
 *  {@code boolean} record component after config documents already existed without it.
 *  Spring Data can't bind a missing/null value into a primitive component, so reading any
 *  pre-existing document threw "Parameter isTool must not be null" on every /orders load. */
@SpringBootTest
class BusinessConfigLegacyDataTest {

    @Autowired MongoOperations mongo;
    @Autowired BusinessConfigRepository repository;

    @Test
    void readsAConfigDocumentPersistedBeforeIsToolExisted() {
        String groupId = "legacy-config-test-group";
        mongo.getCollection("orderTrackerBusinessConfig").insertOne(new Document("_id", groupId)
                .append("overheadPercentage", 0.15)
                .append("profitMarginPercentage", 0.20)
                .append("currency", "INR")
                .append("individualOrderTypeCode", "01")
                .append("bulkOrderTypeCode", "02")
                .append("mandatoryItemTypes", List.of(
                        new Document("itemKey", "wool").append("label", "Wool").append("allowedValues", null)
                        // no "isTool" key at all — exactly what every pre-existing document looked like
                ))
                .append("workStages", List.of()));

        BusinessConfig loaded = repository.findById(groupId).orElseThrow();

        assertThat(loaded.getMandatoryItemTypes()).hasSize(1);
        assertThat(loaded.getMandatoryItemTypes().get(0).isTool()).isFalse();

        mongo.remove(new Query(Criteria.where("_id").is(groupId)), "orderTrackerBusinessConfig");
    }

    /** Same class of bug as isTool, caught live in prod right after that fix shipped:
     *  workStages documents written before splitTracked existed have no key for it at all.
     *  Unlike isTool, the correct legacy default isn't a blanket false — Crocheting/Assembly
     *  were always meant to be split-tracked, so defaulting them to false would silently
     *  corrupt every existing bulk order's completion %. */
    @Test
    void readsAConfigDocumentPersistedBeforeSplitTrackedExisted() {
        String groupId = "legacy-config-test-group-2";
        mongo.getCollection("orderTrackerBusinessConfig").insertOne(new Document("_id", groupId)
                .append("overheadPercentage", 0.15)
                .append("profitMarginPercentage", 0.20)
                .append("currency", "INR")
                .append("individualOrderTypeCode", "03")
                .append("bulkOrderTypeCode", "04")
                .append("mandatoryItemTypes", List.of())
                .append("workStages", List.of(
                        new Document("stageKey", "crocheting").append("label", "Crocheting").append("sequenceOrder", 1),
                        new Document("stageKey", "assembly").append("label", "Assembly").append("sequenceOrder", 2),
                        new Document("stageKey", "packaging").append("label", "Packaging").append("sequenceOrder", 3),
                        new Document("stageKey", "shipment").append("label", "Shipment").append("sequenceOrder", 4)
                        // no "splitTracked" key on any of these — exactly what every
                        // pre-existing document looked like before this field existed
                )));

        BusinessConfig loaded = repository.findById(groupId).orElseThrow();

        assertThat(loaded.getWorkStages()).hasSize(4);
        assertThat(loaded.getWorkStages().get(0).splitTracked()).as("crocheting").isTrue();
        assertThat(loaded.getWorkStages().get(1).splitTracked()).as("assembly").isTrue();
        assertThat(loaded.getWorkStages().get(2).splitTracked()).as("packaging").isFalse();
        assertThat(loaded.getWorkStages().get(3).splitTracked()).as("shipment").isFalse();

        mongo.remove(new Query(Criteria.where("_id").is(groupId)), "orderTrackerBusinessConfig");
    }
}
