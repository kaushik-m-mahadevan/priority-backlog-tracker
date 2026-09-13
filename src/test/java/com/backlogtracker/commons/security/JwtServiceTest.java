package com.backlogtracker.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private final JwtService service = new JwtService(new JwtProperties("test123", 60));

    private static User user() {
        return User.builder()
                .id("u-1").name("Test User").email("test123")
                .role(Role.ADMIN).handle("tst").build();
    }

    @Test
    void issuesAndReturnsTheSubjectOnParse() {
        assertThat(service.parseSubject(service.issue(user()))).isEqualTo("u-1");
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String foreign = new JwtService(new JwtProperties("a-totally-different-secret", 60))
                .issue(user());

        assertThatThrownBy(() -> service.parseSubject(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> service.parseSubject("not.a.jwt")).isInstanceOf(JwtException.class);
    }
}
