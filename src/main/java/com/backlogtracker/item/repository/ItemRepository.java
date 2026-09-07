package com.backlogtracker.item.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.domain.ItemScope;
import com.backlogtracker.item.domain.ItemStatus;

public interface ItemRepository extends MongoRepository<Item, String> {

    Optional<Item> findByItemId(String itemId);

    List<Item> findByScope(ItemScope scope);

    List<Item> findByScopeAndStatusIn(ItemScope scope, List<ItemStatus> statuses);

    List<Item> findByScopeAndOwnerId(ItemScope scope, String ownerId);

    long countByCategory(String category);

    long countByPriority(String priority);
}
