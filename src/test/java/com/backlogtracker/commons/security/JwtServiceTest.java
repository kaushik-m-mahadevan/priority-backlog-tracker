package com.backlogtracker.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.backlogtracker.commons.session.domain.UserSession;
import com.backlogtracker.commons.session.service.SessionService;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    // JJWT's parser checks a token's exp claim against the real wall clock internally
    // (not something we can inject) — so every test needing a token that verifies as
    // "still valid" must issue it with the real clock too. Only the expiry test below
    // needs a fixed-in-the-past clock, to manufacture an already-expired token.
    private final JwtService service = new JwtService(new JwtProperties("test123", 60), Clock.systemUTC(), sessions());

    private static SessionService sessions() {
        SessionService sessions = Mockito.mock(SessionService.class);
        Mockito.when(sessions.start(Mockito.any())).thenReturn(UserSession.builder().id("s-1").build());
        return sessions;
    }

    private static User user() {
        return User.builder()
                .id("u-1").name("Test User").email("test123")
                .role(Role.ADMIN).handle("tst").build();
    }

    @Test
    void issuesAndReturnsTheSubjectOnParse() {
        assertThat(service.parse(service.issue(user())).userId()).isEqualTo("u-1");
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String foreign = new JwtService(new JwtProperties("a-totally-different-secret", 60), Clock.systemUTC(), sessions())
                .issue(user());

        assertThatThrownBy(() -> service.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> service.parse("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    /** Deterministic expiry test — previously impossible without a real sleep/wait, since
     *  JwtService called Instant.now() directly at issue time instead of using an injected
     *  Clock like every other time-sensitive service in this codebase. The verify side
     *  (parse) always checks the token's embedded exp claim against the real wall
     *  clock internally (that's the JJWT library's own behavior, not ours to inject) — so
     *  the deterministic part is manufacturing an already-expired token by issuing it with
     *  a Clock fixed safely in the past, rather than needing to wait out a real TTL. */
    @Test
    void rejectsATokenPastItsExpiration() {
        Clock longAgo = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService expiredIssuer = new JwtService(new JwtProperties("test123", 60), longAgo, sessions());
        String token = expiredIssuer.issue(user());

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }
}
