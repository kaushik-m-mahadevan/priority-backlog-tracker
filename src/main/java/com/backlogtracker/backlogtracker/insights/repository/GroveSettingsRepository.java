package com.backlogtracker.backlogtracker.insights.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.insights.domain.GroveSettings;

public interface GroveSettingsRepository extends MongoRepository<GroveSettings, String> {
}
