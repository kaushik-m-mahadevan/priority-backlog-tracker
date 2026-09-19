package com.backlogtracker.backlogtracker.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import com.backlogtracker.commons.counter.domain.Counter;

@SpringBootTest
class ItemIdGeneratorTest {

    @Autowired
    ItemIdGenerator itemIdGenerator;

    @Autowired
    MongoOperations mongo;

    @BeforeEach
    void clear() {
        mongo.remove(new Query(), Counter.class);
    }

    @Test
    void formatsItemIds() {
        assertThat(itemIdGenerator.next()).isEqualTo("ITM-001");
        assertThat(itemIdGenerator.next()).isEqualTo("ITM-002");
    }
}
