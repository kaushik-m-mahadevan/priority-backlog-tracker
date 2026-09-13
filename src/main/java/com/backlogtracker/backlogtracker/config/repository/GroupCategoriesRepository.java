package com.backlogtracker.backlogtracker.config.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.config.domain.GroupCategories;

public interface GroupCategoriesRepository extends MongoRepository<GroupCategories, String> {
}
