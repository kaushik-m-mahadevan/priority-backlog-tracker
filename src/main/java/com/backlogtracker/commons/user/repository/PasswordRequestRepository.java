package com.backlogtracker.commons.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.user.domain.PasswordRequest;
import com.backlogtracker.commons.user.domain.PasswordRequest.Status;

public interface PasswordRequestRepository extends MongoRepository<PasswordRequest, String> {

    Optional<PasswordRequest> findByUserIdAndStatus(String userId, Status status);

    boolean existsByUserIdAndStatus(String userId, Status status);

    List<PasswordRequest> findByStatusOrderByCreatedAtDesc(Status status);
}
