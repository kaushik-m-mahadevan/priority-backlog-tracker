package com.backlogtracker.materialinventory.needle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.needle.domain.NeedleInventoryEntry;

public interface NeedleInventoryEntryRepository extends MongoRepository<NeedleInventoryEntry, String> {

    List<NeedleInventoryEntry> findByGroupId(String groupId);

    List<NeedleInventoryEntry> findByGroupIdAndUserId(String groupId, String userId);

    List<NeedleInventoryEntry> findByGroupIdAndNeedleTypeId(String groupId, String needleTypeId);

    Optional<NeedleInventoryEntry> findByGroupIdAndUserIdAndNeedleTypeId(String groupId, String userId, String needleTypeId);
}
