package com.backlogtracker.insights.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.insights.domain.GroveSettings;

public interface GroveSettingsRepository extends MongoRepository<GroveSettings, String> {
}
