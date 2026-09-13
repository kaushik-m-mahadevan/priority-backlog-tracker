package com.backlogtracker.commons.user.dto;

import java.time.Instant;

import com.backlogtracker.commons.user.domain.PasswordRequest;

public record PasswordRequestView(
        String id, String userName, String userEmail, String type, String status,
        Instant createdAt) {

    public static PasswordRequestView of(PasswordRequest r) {
        return new PasswordRequestView(r.getId(), r.getUserName(), r.getUserEmail(),
                r.getType().name(), r.getStatus().name(), r.getCreatedAt());
    }
}
