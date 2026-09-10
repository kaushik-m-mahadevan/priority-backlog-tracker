package com.backlogtracker.user.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A password change routed through an admin (design: no self-service password setter —
 * the friction is deliberate). {@code CHANGE} carries the user's chosen hash, applied on
 * approval; {@code RESET} (from the login screen) carries none — the admin sets a
 * temporary one when approving.
 */
@Document("passwordRequests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRequest {

    public enum Type { CHANGE, RESET }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private String id;

    @Indexed
    private String userId;
    private String userName;
    private String userEmail;

    private Type type;
    private Status status;

    /** CHANGE only: the bcrypt hash of the user's requested new password. Never returned. */
    private String newPasswordHash;

    @CreatedDate
    private Instant createdAt;
    private Instant decidedAt;
    private String decidedByUserId;
}
