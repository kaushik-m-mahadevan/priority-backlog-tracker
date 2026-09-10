package com.backlogtracker.user.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.dto.UserSummary;
import com.backlogtracker.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;

    /** Active accounts — for owner labels, the assignee picker and group member lists. */
    @GetMapping
    public List<UserSummary> list() {
        return users.findAll().stream()
                .filter(u -> u.getStatus() != AccountStatus.PENDING)
                .map(UserSummary::of)
                .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
