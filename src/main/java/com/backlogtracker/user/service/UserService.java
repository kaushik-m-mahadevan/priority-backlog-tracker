package com.backlogtracker.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public Optional<User> findById(String id) {
        return repository.findById(id);
    }

    /** Current Owners — used for archival-request quorum (design §18) in a later step. */
    public List<User> owners() {
        return repository.findByRole(Role.OWNER);
    }
}
