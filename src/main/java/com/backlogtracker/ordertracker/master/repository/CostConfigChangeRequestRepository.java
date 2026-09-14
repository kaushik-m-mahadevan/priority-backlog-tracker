package com.backlogtracker.ordertracker.master.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.CostConfigChangeRequest;
import com.backlogtracker.ordertracker.master.domain.CostConfigChangeStatus;

public interface CostConfigChangeRequestRepository extends MongoRepository<CostConfigChangeRequest, String> {

    List<CostConfigChangeRequest> findByGroupId(String groupId);

    Optional<CostConfigChangeRequest> findByGroupIdAndStatus(String groupId, CostConfigChangeStatus status);
}
