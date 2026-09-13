package com.backlogtracker.backlogtracker.archive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.archive.domain.ArchivedItem;

public interface ArchivedItemRepository extends MongoRepository<ArchivedItem, String> {

    Page<ArchivedItem> findByGroupIdOrderByMovedAtDesc(String groupId, Pageable pageable);

    List<ArchivedItem> findByGroupId(String groupId);

    Optional<ArchivedItem> findByItemId(String itemId);
}
