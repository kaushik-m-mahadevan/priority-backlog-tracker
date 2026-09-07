package com.backlogtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private final JwtService service = new JwtService(new JwtProperties("test123", 60));

    private static User user() {
        return User.builder()
                .id("u-1").name("Test User").email("test123")
                .role(Role.OWNER).userCode("TST").build();
    }

    @Test
    void issuesAndParsesRoundTrip() {
        AuthUser parsed = service.parse(service.issue(user()));

        assertThat(parsed.id()).isEqualTo("u-1");
        assertThat(parsed.email()).isEqualTo("test123");
        assertThat(parsed.name()).isEqualTo("Test User");
        assertThat(parsed.role()).isEqualTo(Role.OWNER);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String foreign = new JwtService(new JwtProperties("a-totally-different-secret", 60))
                .issue(user());

        assertThatThrownBy(() -> service.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> service.parse("not.a.jwt")).isInstanceOf(JwtException.class);
    }
}
