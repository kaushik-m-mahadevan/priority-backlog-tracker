package com.backlogtracker.commons;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Enables {@code @CreatedDate} / {@code @LastModifiedDate} population on documents. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
