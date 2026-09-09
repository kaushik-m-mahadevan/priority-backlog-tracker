package com.backlogtracker.user.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCode(String userCode);

    java.util.List<User> findByRole(Role role);
}
