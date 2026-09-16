package com.backlogtracker.materialinventory.needle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.materialinventory.needle.domain.NeedleKind;
import com.backlogtracker.materialinventory.needle.domain.NeedleType;

public interface NeedleTypeRepository extends MongoRepository<NeedleType, String> {

    List<NeedleType> findByGroupId(String groupId);

    Optional<NeedleType> findByGroupIdAndKindAndSizeIgnoreCase(String groupId, NeedleKind kind, String size);
}
