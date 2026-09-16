package com.backlogtracker.commons.image.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.image.domain.ImageAsset;

public interface ImageAssetRepository extends MongoRepository<ImageAsset, String> {

    List<ImageAsset> findByGroupIdAndOwnerTypeAndOwnerIdOrderBySequenceOrderAsc(
            String groupId, String ownerType, String ownerId);

    long countByGroupIdAndOwnerTypeAndOwnerId(String groupId, String ownerType, String ownerId);
}
