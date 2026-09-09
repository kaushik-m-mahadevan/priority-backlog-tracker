package com.backlogtracker.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.dto.CreateUserRequest;
import com.backlogtracker.user.dto.UpdateUserRequest;
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

    /** Admin approves a pending account. Idempotent for an already-active account. */
    public User approve(String id, String adminId) {
        User u = require(id);
        if (u.getStatus() == AccountStatus.ACTIVE) {
            return u;
        }
        u.setStatus(AccountStatus.ACTIVE);
        u.setApprovedAt(java.time.Instant.now());
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

    /** Self-registration: a USER account in PENDING state. */
    public User register(com.backlogtracker.auth.dto.RegisterRequest r) {
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

    /** Add a team member (admin-only endpoint). */
    public User create(CreateUserRequest r) {
        String email = r.email().trim();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user with that email already exists");
        }
        return repository.save(User.builder()
                .name(r.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(r.password()))
                .role(parseRole(r.role()))
                .status(AccountStatus.ACTIVE)
                .handle(uniqueHandle(deriveHandle(r.name())))
                .build());
    }

    /** Change a member's role and/or reset their password (Owner-only endpoint). */
    public User update(String id, UpdateUserRequest r) {
        User u = repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        if (has(r.role())) {
            u.setRole(parseRole(r.role()));
        }
        if (has(r.password())) {
            u.setPasswordHash(passwordEncoder.encode(r.password()));
        }
        return repository.save(u);
    }

    private static Role parseRole(String s) {
        if (!has(s)) {
            return Role.USER;
        }
        try {
            return Role.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("role must be ADMIN or USER");
        }
    }

    /** A handle seed from the name, e.g. "Priya Shah" -> "priyashah" (clamped to 16). */
    static String deriveHandle(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (slug.length() < 3) {
            slug = "user";
        }
        return slug.substring(0, Math.min(16, slug.length()));
    }

    /** Returns {@code base}, or {@code base2}..{@code base99}, or a random-suffixed form. */
    public String uniqueHandle(String base) {
        if (!repository.existsByHandleIgnoreCase(base)) {
            return base;
        }
        for (int n = 2; n <= 99; n++) {
            if (!repository.existsByHandleIgnoreCase(base + n)) {
                return base + n;
            }
        }
        return base + UUID.randomUUID().toString().substring(0, 4);
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
