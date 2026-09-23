package com.backlogtracker.financetracker.config.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.financetracker.config.domain.FinanceBusinessConfig;

public interface FinanceBusinessConfigRepository extends MongoRepository<FinanceBusinessConfig, String> {
}
