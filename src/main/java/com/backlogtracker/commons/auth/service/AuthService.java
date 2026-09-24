package com.backlogtracker.commons.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.backlogtracker.commons.auth.dto.LoginRequest;
import com.backlogtracker.commons.auth.dto.LoginResponse;
import com.backlogtracker.commons.auth.dto.RegisterRequest;
import com.backlogtracker.commons.auth.dto.UserView;
import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationOrchestrator;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationOrchestrator notificationOrchestrator;

    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return new LoginResponse(jwtService.issue(user), UserView.of(user));
    }

    /** Self-registration → a PENDING account plus a token so the SPA can show the wait screen. */
    public LoginResponse register(RegisterRequest request) {
        User user = userService.register(request);
        notificationOrchestrator.notifyAdmins(NotificationType.SIGNUP_PENDING, "New signup",
                user.getName() + " (" + user.getEmail() + ") signed up and is waiting for approval.", "/admin");
        return new LoginResponse(jwtService.issue(user), UserView.of(user));
    }
}
