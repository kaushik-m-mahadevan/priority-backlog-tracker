package com.backlogtracker.commons.group.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.group.domain.PlatformConfig;

public interface PlatformConfigRepository extends MongoRepository<PlatformConfig, String> {
}
