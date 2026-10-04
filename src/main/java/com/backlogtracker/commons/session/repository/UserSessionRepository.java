package com.backlogtracker.commons.session.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.session.domain.UserSession;

public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    List<UserSession> findAllByOrderByLoginAtDesc(Pageable pageable);

    List<UserSession> findByUserIdOrderByLoginAtDesc(String userId, Pageable pageable);

    List<UserSession> findByLogoutAtIsNullAndLastActivityAtBefore(Instant cutoff);
}
