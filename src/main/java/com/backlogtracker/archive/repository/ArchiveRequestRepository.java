package com.backlogtracker.archive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.archive.domain.ArchiveRequest;
import com.backlogtracker.archive.domain.ArchiveRequest.Status;

public interface ArchiveRequestRepository extends MongoRepository<ArchiveRequest, String> {

    Optional<ArchiveRequest> findByItemIdAndStatus(String itemId, Status status);

    List<ArchiveRequest> findByGroupIdAndStatus(String groupId, Status status);

    boolean existsByItemIdAndStatus(String itemId, Status status);
}
