package com.backlogtracker.ordertracker.master.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.ComponentTemplate;

public interface ComponentTemplateRepository extends MongoRepository<ComponentTemplate, String> {

    List<ComponentTemplate> findByGroupId(String groupId);
}
