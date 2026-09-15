package com.backlogtracker.commons.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.approval.domain.ApprovalRequest;
import com.backlogtracker.commons.approval.domain.ApprovalStatus;

public interface ApprovalRequestRepository extends MongoRepository<ApprovalRequest, String> {

    Optional<ApprovalRequest> findByGroupIdAndKindAndStatus(String groupId, String kind, ApprovalStatus status);

    List<ApprovalRequest> findByGroupIdAndStatus(String groupId, ApprovalStatus status);

    List<ApprovalRequest> findByGroupIdAndKind(String groupId, String kind);

    List<ApprovalRequest> findByGroupId(String groupId);
}
