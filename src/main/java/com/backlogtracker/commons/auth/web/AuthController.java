package com.backlogtracker.commons.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.commons.auth.dto.LoginRequest;
import com.backlogtracker.commons.auth.dto.LoginResponse;
import com.backlogtracker.commons.auth.dto.RegisterRequest;
import com.backlogtracker.commons.auth.dto.UserView;
import com.backlogtracker.commons.auth.service.AuthService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.JwtAuthenticationFilter;
import com.backlogtracker.commons.security.RequiresUser;
import com.backlogtracker.commons.session.dto.SessionPolicyView;
import com.backlogtracker.commons.session.service.SessionService;
import com.backlogtracker.commons.user.dto.ChangePasswordRequest;
import com.backlogtracker.commons.user.dto.ForgotPasswordRequest;
import com.backlogtracker.commons.user.service.PasswordRequestService;
import com.backlogtracker.commons.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordRequestService passwordRequestService;
    private final UserService userService;
    private final SessionService sessionService;

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

    /** Ends this login session and invalidates its token. {@code inactive} is the browser
     *  reporting that its idle timer fired (see {@code SessionService.end}). */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE) String sessionId,
                       @RequestParam(defaultValue = "false") boolean inactive) {
        sessionService.end(sessionId, inactive);
    }

    /** No-op: being authenticated is what refreshes the session's activity clock. The browser
     *  calls this while the user is interacting but not otherwise hitting the API. */
    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat() {
    }

    /** The inactivity timeout, so the browser's idle timer matches the server's. */
    @GetMapping("/session-policy")
    public SessionPolicyView sessionPolicy() {
        return new SessionPolicyView((int) sessionService.timeout().toMinutes());
    }

    /** Signed-in user requests a password change — an admin has to approve it. */
    @PostMapping("/password-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresUser
    public void requestPasswordChange(@Valid @RequestBody ChangePasswordRequest request,
                                      @AuthenticationPrincipal AuthUser actor) {
        passwordRequestService.requestChange(actor, request.currentPassword(), request.newPassword(), request.replaceExisting());
    }

    /** Login-screen "forgot password" — notifies an admin, always 202 (no account probing). */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordRequestService.requestReset(request.email());
    }
}
