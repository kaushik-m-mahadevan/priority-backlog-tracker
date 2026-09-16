package com.backlogtracker.productcatalog.colorway.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.productcatalog.colorway.domain.Colorway;

public interface ColorwayRepository extends MongoRepository<Colorway, String> {

    List<Colorway> findByGroupId(String groupId);

    Optional<Colorway> findByGroupIdAndNameIgnoreCaseAndColourIgnoreCase(String groupId, String name, String colour);
}
