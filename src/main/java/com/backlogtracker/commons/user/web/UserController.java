package com.backlogtracker.commons.user.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.auth.dto.UserView;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.dto.UpdateProfileRequest;
import com.backlogtracker.commons.user.dto.UserSummary;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.commons.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final UserService userService;

    /** Active accounts — for owner labels, the assignee picker and group member lists. */
    @GetMapping
    public List<UserSummary> list() {
        return users.findAll().stream()
                .filter(u -> u.getStatus() != AccountStatus.PENDING)
                .map(UserSummary::of)
                .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The caller updates their own display name. */
    @PatchMapping("/me")
    @RequiresUser
    public UserView updateMe(@Valid @RequestBody UpdateProfileRequest request,
                             @AuthenticationPrincipal AuthUser actor) {
        return UserView.of(userService.updateProfile(actor.id(), request.name()));
    }
}
