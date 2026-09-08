package com.backlogtracker.archive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.archive.domain.ArchivedItem;

public interface ArchivedItemRepository extends MongoRepository<ArchivedItem, String> {

    List<ArchivedItem> findAllByOrderByMovedAtDesc();

    Page<ArchivedItem> findAllByOrderByMovedAtDesc(Pageable pageable);

    Optional<ArchivedItem> findByItemId(String itemId);
}
