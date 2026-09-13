package com.backlogtracker.ordertracker.master.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.Creator;

public interface CreatorRepository extends MongoRepository<Creator, String> {

    Optional<Creator> findByGroupIdAndUserId(String groupId, String userId);

    List<Creator> findByGroupId(String groupId);
}
