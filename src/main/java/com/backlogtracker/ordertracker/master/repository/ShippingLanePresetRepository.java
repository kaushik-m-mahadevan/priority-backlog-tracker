package com.backlogtracker.ordertracker.master.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.ShippingLanePreset;

public interface ShippingLanePresetRepository extends MongoRepository<ShippingLanePreset, String> {

    List<ShippingLanePreset> findByGroupId(String groupId);
}
