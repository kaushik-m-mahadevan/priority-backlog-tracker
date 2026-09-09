package com.backlogtracker.user.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.security.RequiresOwner;
import com.backlogtracker.user.dto.CreateUserRequest;
import com.backlogtracker.user.dto.UpdateUserRequest;
import com.backlogtracker.user.dto.UserSummary;
import com.backlogtracker.user.repository.UserRepository;
import com.backlogtracker.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final UserService userService;

    /** Everyone on the team — for owner labels and the assignee picker. */
    @GetMapping
    public List<UserSummary> list() {
        return users.findAll().stream()
                .map(UserSummary::of)
                .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Add a team member (design §8 — Owner-only). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresOwner
    public UserSummary create(@Valid @RequestBody CreateUserRequest request) {
        return UserSummary.of(userService.create(request));
    }

    /** Change a member's role or reset their password (Owner-only). */
    @PatchMapping("/{id}")
    @RequiresOwner
    public UserSummary update(@PathVariable String id,
                             @Valid @RequestBody UpdateUserRequest request) {
        return UserSummary.of(userService.update(id, request));
    }
}
