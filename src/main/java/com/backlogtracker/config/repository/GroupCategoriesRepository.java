package com.backlogtracker.config.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.config.domain.GroupCategories;

public interface GroupCategoriesRepository extends MongoRepository<GroupCategories, String> {
}
