package com.backlogtracker.commons.session.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One login to logout span for one user. The JWT carries this document's id as its {@code sid}
 * claim, so closing the session (sign-out or inactivity) also invalidates that token.
 *
 * <p>{@code userName}/{@code userEmail} are snapshots so the audit trail stays readable if the
 * account is later renamed or deleted.
 */
@Document("userSessions")
@CompoundIndex(name = "user_login", def = "{'userId': 1, 'loginAt': -1}")
@CompoundIndex(name = "open_by_activity", def = "{'logoutAt': 1, 'lastActivityAt': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    private String id;

    private String userId;
    private String userName;
    private String userEmail;

    private Instant loginAt;

    /** Last time the user did something. Written at most every
     *  {@code SessionService.TOUCH_THROTTLE}, so it can lag the true last action slightly. */
    private Instant lastActivityAt;

    /** Null while the session is open. For an inactivity close this is
     *  {@code lastActivityAt + timeout} (when the user effectively left), not the later
     *  moment the server noticed. */
    private Instant logoutAt;

    private LogoutReason logoutReason;
}
