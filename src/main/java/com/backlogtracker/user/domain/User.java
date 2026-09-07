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
 * A member of the founding team (design §2, §8).
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

    /** Short code used in personal item IDs, e.g. {@code P-TST-014} (design §20). */
    @Indexed(unique = true)
    private String userCode;

    @CreatedDate
    private Instant createdAt;
}
