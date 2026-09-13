package com.backlogtracker.ordertracker.master.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.PresetOption;

public interface PresetOptionRepository extends MongoRepository<PresetOption, String> {

    List<PresetOption> findByGroupId(String groupId);
}
