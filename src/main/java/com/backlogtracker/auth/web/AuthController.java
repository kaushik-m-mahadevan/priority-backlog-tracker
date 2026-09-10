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
import com.backlogtracker.security.RequiresUser;
import com.backlogtracker.user.dto.ChangePasswordRequest;
import com.backlogtracker.user.dto.ForgotPasswordRequest;
import com.backlogtracker.user.service.PasswordRequestService;
import com.backlogtracker.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordRequestService passwordRequestService;
    private final UserService userService;

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

    /** Returns the currently authenticated user (loaded fresh so preferences are current). */
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser principal) {
        return userService.findById(principal.id())
                .map(UserView::of)
                .orElseGet(() -> UserView.of(principal));
    }

    /** Signed-in user requests a password change — an admin has to approve it. */
    @PostMapping("/password-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresUser
    public void requestPasswordChange(@Valid @RequestBody ChangePasswordRequest request,
                                      @AuthenticationPrincipal AuthUser actor) {
        passwordRequestService.requestChange(actor, request.currentPassword(), request.newPassword());
    }

    /** Login-screen "forgot password" — notifies an admin, always 202 (no account probing). */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordRequestService.requestReset(request.email());
    }
}
