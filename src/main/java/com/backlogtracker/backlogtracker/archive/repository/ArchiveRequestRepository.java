package com.backlogtracker.backlogtracker.archive.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest;
import com.backlogtracker.backlogtracker.archive.domain.ArchiveRequest.Status;

public interface ArchiveRequestRepository extends MongoRepository<ArchiveRequest, String> {

    List<ArchiveRequest> findByGroupIdAndStatus(String groupId, Status status);

    boolean existsByItemIdAndStatus(String itemId, Status status);
}
