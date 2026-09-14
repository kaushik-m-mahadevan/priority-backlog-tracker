package com.backlogtracker.commons.user.domain;

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

    /** Short, unique, user-facing handle — the alternative invite target to email. A
     *  partial filter (not {@code sparse}) — legacy documents have none until
     *  {@code LegacyDataMigration} renames it, and a full-document resave of one of those
     *  (e.g. {@code UserService.approve}/{@code updateProfile}) writes {@code handle: null}
     *  explicitly; {@code sparse} doesn't exclude that from the index (same root cause as
     *  the {@code Item.linkedOrderId} outage), so a second such resave would 11000.
     *
     *  <p>Named differently from the old {@code sparse} index it replaces (still live in
     *  prod under the name "handle") so the two can coexist at startup instead of Mongo
     *  rejecting a same-named index with different options; {@code LegacyDataMigration}
     *  drops the superseded one once this one exists. */
    @Indexed(name = "handle_unique_partial", unique = true, partialFilter = "{'handle': {'$type': 'string'}}")
    private String handle;

    @CreatedDate
    private Instant createdAt;

    private Instant approvedAt;
    private String approvedByUserId;

    /** Legacy nulls mean the account predates the onboarding lifecycle → active. */
    public boolean isActive() {
        return status == null || status == AccountStatus.ACTIVE;
    }
}
