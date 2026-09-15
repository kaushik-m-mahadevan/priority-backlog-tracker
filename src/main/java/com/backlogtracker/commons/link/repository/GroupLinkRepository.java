package com.backlogtracker.commons.link.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.link.domain.GroupLink;

public interface GroupLinkRepository extends MongoRepository<GroupLink, String> {

    Optional<GroupLink> findByGroupIdAAndAppletKeyB(String groupIdA, String appletKeyB);

    Optional<GroupLink> findByGroupIdBAndAppletKeyA(String groupIdB, String appletKeyA);

    List<GroupLink> findByGroupIdAOrGroupIdB(String groupIdA, String groupIdB);

    void deleteByGroupIdAOrGroupIdB(String groupIdA, String groupIdB);
}
