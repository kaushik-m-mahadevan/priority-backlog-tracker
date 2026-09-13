package com.backlogtracker.ordertracker.master.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.ordertracker.master.domain.LocationCode;

public interface LocationCodeRepository extends MongoRepository<LocationCode, String> {

    Optional<LocationCode> findByGroupIdAndLocationNameIgnoreCase(String groupId, String locationName);
}
