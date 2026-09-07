package com.backlogtracker.config.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.config.domain.AppConfig;

public interface ConfigRepository extends MongoRepository<AppConfig, String> {
}
