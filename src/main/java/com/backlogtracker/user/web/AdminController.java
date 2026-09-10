package com.backlogtracker.user.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestBody;

import com.backlogtracker.security.AuthUser;
import com.backlogtracker.security.RequiresAdmin;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.dto.ApprovePasswordRequest;
import com.backlogtracker.user.dto.PasswordRequestView;
import com.backlogtracker.user.dto.UserSummary;
import com.backlogtracker.user.repository.UserRepository;
import com.backlogtracker.user.service.PasswordRequestService;
import com.backlogtracker.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** Admin-only onboarding console (design: roles / self-onboarding). */
@RestController
@RequestMapping("/api/admin")
@RequiresAdmin
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final UserRepository users;
    private final PasswordRequestService passwordRequests;

    /** Accounts awaiting approval, oldest first. */
    @GetMapping("/pending-users")
    public List<UserSummary> pending() {
        return userService.byStatus(AccountStatus.PENDING).stream()
                .sorted(Comparator.comparing(u -> u.getCreatedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(UserSummary::of)
                .toList();
    }

    /** The full roster. */
    @GetMapping("/users")
    public List<UserSummary> roster() {
        return users.findAll().stream()
                .map(UserSummary::of)
                .sorted(Comparator.comparing(UserSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @PostMapping("/users/{id}/approve")
    public UserSummary approve(@PathVariable String id, @AuthenticationPrincipal AuthUser admin) {
        return UserSummary.of(userService.approve(id, admin.id()));
    }

    @PostMapping("/users/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable String id) {
        userService.reject(id);
    }

    // ---- password requests (design: password changes go through an admin) ----

    @GetMapping("/password-requests")
    public List<PasswordRequestView> passwordRequests() {
        return passwordRequests.pending().stream().map(PasswordRequestView::of).toList();
    }

    @PostMapping("/password-requests/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approvePasswordRequest(@PathVariable String id,
                                       @RequestBody(required = false) ApprovePasswordRequest body,
                                       @AuthenticationPrincipal AuthUser admin) {
        passwordRequests.approve(id, admin, body == null ? null : body.temporaryPassword());
    }

    @PostMapping("/password-requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectPasswordRequest(@PathVariable String id,
                                      @AuthenticationPrincipal AuthUser admin) {
        passwordRequests.reject(id, admin);
    }
}
