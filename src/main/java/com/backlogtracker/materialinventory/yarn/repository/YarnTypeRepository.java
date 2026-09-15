package com.backlogtracker.materialinventory.yarn.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.yarn.domain.YarnType;

public interface YarnTypeRepository extends MongoRepository<YarnType, String> {

    List<YarnType> findByGroupId(String groupId);

    Optional<YarnType> findByGroupIdAndBrandIgnoreCaseAndThicknessIgnoreCaseAndColourIgnoreCase(
            String groupId, String brand, String thickness, String colour);
}
