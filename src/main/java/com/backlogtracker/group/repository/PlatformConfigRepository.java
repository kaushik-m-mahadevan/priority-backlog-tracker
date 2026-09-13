package com.backlogtracker.group.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.group.domain.PlatformConfig;

public interface PlatformConfigRepository extends MongoRepository<PlatformConfig, String> {
}
