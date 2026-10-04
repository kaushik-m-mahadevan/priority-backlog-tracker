package com.backlogtracker.commons.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.backlogtracker.commons.crypto.Sha256;
import com.backlogtracker.commons.session.service.SessionService;
import com.backlogtracker.commons.user.domain.User;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies stateless HS256 JWTs (design §9).
 *
 * <p>The configured secret is hashed to a fixed 256-bit key so a short dev secret such as
 * {@code test123} works while still allowing a strong production secret via
 * {@code JWT_SECRET}.
 *
 * <p>Issuing a token opens a tracked session ({@link SessionService}); its id rides in the
 * {@code sid} claim so sign-out and inactivity expiry can invalidate the token server-side
 * even though it is otherwise stateless. A token with no {@code sid} is never accepted.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;
    private final SessionService sessions;

    /** What a verified token asserts: who, and which tracked session. */
    public record TokenClaims(String userId, String sessionId) {
    }

    public JwtService(JwtProperties props, Clock clock, SessionService sessions) {
        this.key = Keys.hmacShaKeyFor(Sha256.digest(props.secret()));
        this.ttl = Duration.ofMinutes(props.expirationMinutes());
        this.clock = clock;
        this.sessions = sessions;
    }

    public String issue(User user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(user.getId())
                .claim("sid", sessions.start(user).getId())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies a token and returns its user id and session id. Role and status are read
     * fresh from the database by the auth filter, never trusted from the token.
     *
     * @throws JwtException if the token is malformed, tampered, expired, or has no session
     */
    public TokenClaims parse(String token) {
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String sessionId = claims.get("sid", String.class);
        if (sessionId == null) {
            throw new JwtException("Token has no session");
        }
        return new TokenClaims(claims.getSubject(), sessionId);
    }
}
