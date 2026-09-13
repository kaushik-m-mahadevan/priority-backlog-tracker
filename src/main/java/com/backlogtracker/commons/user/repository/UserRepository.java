package com.backlogtracker.commons.user.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByHandleIgnoreCase(String handle);

    boolean existsByHandleIgnoreCase(String handle);

    java.util.List<User> findByRole(Role role);

    java.util.List<User> findByStatus(AccountStatus status);
}
