package com.backlogtracker.user.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.auth.dto.RegisterRequest;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public Optional<User> findById(String id) {
        return repository.findById(id);
    }

    public List<User> admins() {
        return repository.findByRole(Role.ADMIN);
    }

    public List<User> byStatus(AccountStatus status) {
        return repository.findByStatus(status);
    }

    /** Self-registration: a USER account in PENDING state. */
    public User register(RegisterRequest r) {
        if (!r.password().equals(r.confirmPassword())) {
            throw new IllegalArgumentException("Passwords don't match");
        }
        String email = r.email().trim();
        String handle = r.handle().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered");
        }
        if (repository.existsByHandleIgnoreCase(handle)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That handle is taken");
        }
        return repository.save(User.builder()
                .name(r.name().trim())
                .email(email)
                .handle(handle)
                .passwordHash(passwordEncoder.encode(r.password()))
                .role(Role.USER)
                .status(AccountStatus.PENDING)
                .build());
    }

    /** A user updates their own display name. Email stays fixed (it's the login id). */
    public User updateProfile(String userId, String name) {
        User u = require(userId);
        if (name != null) {
            if (name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank");
            }
            u.setName(name.trim());
        }
        return repository.save(u);
    }

    /** Admin approves a pending account. Idempotent for an already-active account. */
    public User approve(String id, String adminId) {
        User u = require(id);
        if (u.getStatus() == AccountStatus.ACTIVE) {
            return u;
        }
        u.setStatus(AccountStatus.ACTIVE);
        u.setApprovedAt(Instant.now());
        u.setApprovedByUserId(adminId);
        return repository.save(u);
    }

    /** Admin rejects a pending account — the record is deleted, the email frees up. */
    public void reject(String id) {
        User u = require(id);
        if (u.getStatus() != AccountStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending account can be rejected");
        }
        repository.delete(u);
    }

    private User require(String id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
