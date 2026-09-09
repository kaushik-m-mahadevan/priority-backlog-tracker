package com.backlogtracker.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.backlogtracker.auth.dto.LoginRequest;
import com.backlogtracker.auth.dto.LoginResponse;
import com.backlogtracker.auth.dto.RegisterRequest;
import com.backlogtracker.auth.dto.UserView;
import com.backlogtracker.security.JwtService;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return new LoginResponse(jwtService.issue(user), UserView.of(user));
    }

    /** Self-registration → a PENDING account plus a token so the SPA can show the wait screen. */
    public LoginResponse register(RegisterRequest request) {
        User user = userService.register(request);
        return new LoginResponse(jwtService.issue(user), UserView.of(user));
    }
}
