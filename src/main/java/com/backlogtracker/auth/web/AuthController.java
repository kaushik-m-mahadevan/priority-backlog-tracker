package com.backlogtracker.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.auth.dto.LoginRequest;
import com.backlogtracker.auth.dto.LoginResponse;
import com.backlogtracker.auth.dto.RegisterRequest;
import com.backlogtracker.auth.dto.UserView;
import com.backlogtracker.auth.service.AuthService;
import com.backlogtracker.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Exchanges email + password for a JWT. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Self-registration → a PENDING account and a token for the waiting screen. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Returns the currently authenticated user (from the bearer token). */
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser principal) {
        return UserView.of(principal);
    }
}
