package com.backlogtracker.backlogtracker.config.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.config.domain.ConfigHistory;

public interface ConfigHistoryRepository extends MongoRepository<ConfigHistory, String> {

    List<ConfigHistory> findTop30ByOrderByTimestampDesc();
}
