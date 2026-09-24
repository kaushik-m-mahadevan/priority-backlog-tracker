package com.backlogtracker.ordertracker.master.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.AssemblyPreset;

public interface AssemblyPresetRepository extends MongoRepository<AssemblyPreset, String> {

    List<AssemblyPreset> findByGroupId(String groupId);
}
