package com.backlogtracker.materialinventory.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.inventory.domain.InventoryEntry;

public interface InventoryEntryRepository extends MongoRepository<InventoryEntry, String> {

    List<InventoryEntry> findByGroupId(String groupId);

    List<InventoryEntry> findByGroupIdAndUserId(String groupId, String userId);

    List<InventoryEntry> findByGroupIdAndYarnTypeId(String groupId, String yarnTypeId);

    Optional<InventoryEntry> findByGroupIdAndUserIdAndYarnTypeId(String groupId, String userId, String yarnTypeId);
}
