package com.backlogtracker.backlogtracker.item.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;

public interface ItemRepository extends MongoRepository<Item, String> {

    Optional<Item> findByItemId(String itemId);

    List<Item> findByGroupIdAndStatusIn(String groupId, List<ItemStatus> statuses);

    long countByGroupIdAndCategory(String groupId, String category);

    List<Item> findByGroupIdAndCategory(String groupId, String category);

    long countByPriority(String priority);
}
