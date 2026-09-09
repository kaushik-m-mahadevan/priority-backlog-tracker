package com.backlogtracker.user.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account. Roles: {@link Role#ADMIN} / {@link Role#USER}. Lifecycle:
 * {@link AccountStatus#PENDING} → {@link AccountStatus#ACTIVE} on admin approval.
 */
@Document("users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    /** BCrypt hash. Never serialized to clients. */
    @Field("passwordHash")
    private String passwordHash;

    private Role role;

    /** null on legacy documents — treated as {@link AccountStatus#ACTIVE}. */
    private AccountStatus status;

    /** Short, user-facing handle (invite target). Was {@code userCode}. */
    @Indexed(unique = true)
    private String userCode;

    @CreatedDate
    private Instant createdAt;

    private Instant approvedAt;
    private String approvedByUserId;

    /** Legacy nulls mean the account predates the onboarding lifecycle → active. */
    public boolean isActive() {
        return status == null || status == AccountStatus.ACTIVE;
    }
}
