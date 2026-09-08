package com.backlogtracker.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Registers a {@link MongoTransactionManager} only when
 * {@code app.mongo.transactions.enabled=true}. MongoDB multi-document transactions need a
 * replica set (Atlas M0 is one); the local standalone / embedded server is not, so this
 * stays off by default and {@code ArchiveService} falls back to sequential writes.
 */
@Configuration
@ConditionalOnProperty(name = "app.mongo.transactions.enabled", havingValue = "true")
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
