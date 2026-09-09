package com.backlogtracker.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    /** Current Owners — used for archival-request quorum (design §18) in a later step. */
    public List<User> owners() {
        return repository.findByRole(Role.OWNER);
    }

    /** Add a team member (Owner-only endpoint). */
    public User create(CreateUserRequest r) {
        String email = r.email().trim();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user with that email already exists");
        }
        String code = has(r.userCode()) ? r.userCode().trim().toUpperCase() : deriveCode(r.name());
        return repository.save(User.builder()
                .name(r.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(r.password()))
                .role(parseRole(r.role()))
                .userCode(uniqueCode(code))
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
            return Role.OWNER;
        }
        try {
            return Role.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("role must be OWNER, CONTRIBUTOR or VIEWER");
        }
    }

    /** First 3 alphanumerics of the name, e.g. "Priya Shah" -> "PRI". */
    private static String deriveCode(String name) {
        String letters = name.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return letters.isEmpty() ? "USR" : letters.substring(0, Math.min(3, letters.length()));
    }

    private String uniqueCode(String base) {
        if (!repository.existsByUserCode(base)) {
            return base;
        }
        for (int n = 2; n <= 99; n++) {
            String candidate = base + n;
            if (!repository.existsByUserCode(candidate)) {
                return candidate;
            }
        }
        return base + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
