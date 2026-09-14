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
}
