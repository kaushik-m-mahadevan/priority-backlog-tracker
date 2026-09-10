package com.backlogtracker.group.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.group.domain.Group;

public interface GroupRepository extends MongoRepository<Group, String> {

    List<Group> findByMemberIdsContaining(String userId);

    long countByMemberIdsContaining(String userId);
}
