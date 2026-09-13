package com.backlogtracker.ordertracker.master.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.BusinessConfig;

public interface BusinessConfigRepository extends MongoRepository<BusinessConfig, String> {
}
