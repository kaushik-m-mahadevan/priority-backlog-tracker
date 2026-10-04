package com.backlogtracker.commons.session.dto;

import java.time.Duration;
import java.time.Instant;

import com.backlogtracker.commons.session.domain.LogoutReason;
import com.backlogtracker.commons.session.domain.UserSession;

public record SessionView(String id, String userId, String userName, String userEmail,
                          Instant loginAt, Instant lastActivityAt, Instant logoutAt,
                          LogoutReason logoutReason, boolean active, Long durationSeconds) {

    public static SessionView of(UserSession s) {
        Instant end = s.getLogoutAt() != null ? s.getLogoutAt() : s.getLastActivityAt();
        return new SessionView(s.getId(), s.getUserId(), s.getUserName(), s.getUserEmail(),
                s.getLoginAt(), s.getLastActivityAt(), s.getLogoutAt(), s.getLogoutReason(),
                s.getLogoutAt() == null,
                end == null || s.getLoginAt() == null ? null : Duration.between(s.getLoginAt(), end).toSeconds());
    }
}
