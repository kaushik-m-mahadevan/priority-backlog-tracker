package com.backlogtracker.commons.user.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.backlogtracker.commons.notification.domain.NotificationType;
import com.backlogtracker.commons.notification.service.NotificationService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.user.domain.PasswordRequest;
import com.backlogtracker.commons.user.domain.PasswordRequest.Status;
import com.backlogtracker.commons.user.domain.PasswordRequest.Type;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.PasswordRequestRepository;
import com.backlogtracker.commons.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Password changes always pass through an admin (design: the friction makes people
 * remember their passwords). The admin approves blind — the request never exposes the
 * new password.
 */
@Service
@RequiredArgsConstructor
public class PasswordRequestService {

    private final PasswordRequestRepository requests;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    /** Signed-in user asks to change their password; the new one is held until approval.
     *  ad-7: a pending request blocks this UNLESS {@code replaceExisting} is set, in which
     *  case the old one is marked SUPERSEDED first — same "replace pending request?"
     *  confirmation flow the frontend can now offer on either password path. */
    public void requestChange(AuthUser actor, String currentPassword, String newPassword, boolean replaceExisting) {
        User user = users.findById(actor.id()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new password must be different");
        }
        supersedeOrReject(user.getId(), replaceExisting);
        requests.save(PasswordRequest.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .type(Type.CHANGE)
                .status(Status.PENDING)
                .newPasswordHash(passwordEncoder.encode(newPassword))
                .build());
    }

    /** Login-screen "forgot password". Silent about whether the account exists — so unlike
     *  {@link #requestChange}, this never surfaces a "replace pending request?" prompt
     *  (that would itself leak whether the account/a prior request exists, defeating the
     *  whole point). Instead it unifies the underlying handling a different way (ad-7):
     *  resubmitting always quietly supersedes whatever was pending, rather than the old
     *  behavior of silently doing nothing on a second attempt. */
    public void requestReset(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            requests.findByUserIdAndStatus(user.getId(), Status.PENDING)
                    .ifPresent(req -> decide(req, Status.SUPERSEDED, null));
            requests.save(PasswordRequest.builder()
                    .userId(user.getId())
                    .userName(user.getName())
                    .userEmail(user.getEmail())
                    .type(Type.RESET)
                    .status(Status.PENDING)
                    .build());
        });
    }

    public List<PasswordRequest> pending() {
        return requests.findByStatusOrderByCreatedAtDesc(Status.PENDING);
    }

    /**
     * Approve a request. CHANGE applies the held hash; RESET needs a {@code temporaryPassword}
     * the admin types in and passes to the person out of band.
     */
    public void approve(String id, AuthUser admin, String temporaryPassword) {
        PasswordRequest req = pendingReq(id);
        User user = users.findById(req.getUserId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User no longer exists"));

        if (req.getType() == Type.CHANGE) {
            user.setPasswordHash(req.getNewPasswordHash());
        } else {
            if (temporaryPassword == null || temporaryPassword.length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Set a temporary password of at least 8 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        }
        users.save(user);
        decide(req, Status.APPROVED, admin.id());
        notificationService.info(user.getId(), NotificationType.PASSWORD_RESULT,
                req.getType() == Type.CHANGE
                        ? "Your password change was approved — the new password is now active."
                        : "An admin set a temporary password for your account. Ask them for it, "
                                + "then change it from Settings.");
    }

    public void reject(String id, AuthUser admin) {
        PasswordRequest req = pendingReq(id);
        decide(req, Status.REJECTED, admin.id());
        notificationService.info(req.getUserId(), NotificationType.PASSWORD_RESULT,
                "Your password request was declined. Talk to an admin if you still need help.");
    }

    private void decide(PasswordRequest req, Status status, String adminId) {
        req.setStatus(status);
        req.setDecidedAt(Instant.now());
        req.setDecidedByUserId(adminId);
        req.setNewPasswordHash(null); // don't keep it around once decided
        requests.save(req);
    }

    /** ad-7: {@code replaceExisting=false} preserves the old blocking behavior (409, so
     *  the client can ask "replace pending request?"); {@code true} supersedes the old
     *  pending request instead of blocking. */
    private void supersedeOrReject(String userId, boolean replaceExisting) {
        requests.findByUserIdAndStatus(userId, Status.PENDING).ifPresent(existing -> {
            if (!replaceExisting) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already have a password request awaiting an admin");
            }
            decide(existing, Status.SUPERSEDED, null);
        });
    }

    private PasswordRequest pendingReq(String id) {
        PasswordRequest req = requests.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (req.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already " + req.getStatus());
        }
        return req;
    }
}
