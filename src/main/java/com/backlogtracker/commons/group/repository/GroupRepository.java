package com.backlogtracker.commons.group.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.group.domain.Group;

public interface GroupRepository extends MongoRepository<Group, String> {

    List<Group> findByMemberIdsContaining(String userId);

    long countByMemberIdsContaining(String userId);

    long countByMemberIdsContainingAndAppletKey(String userId, String appletKey);
}
