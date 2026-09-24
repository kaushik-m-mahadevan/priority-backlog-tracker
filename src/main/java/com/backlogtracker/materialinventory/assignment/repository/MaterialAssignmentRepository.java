package com.backlogtracker.materialinventory.assignment.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.assignment.domain.MaterialAssignment;

public interface MaterialAssignmentRepository extends MongoRepository<MaterialAssignment, String> {

    List<MaterialAssignment> findByGroupId(String groupId);
}
