package com.backlogtracker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies stateless HS256 JWTs (design §9).
 *
 * <p>The configured secret is hashed to a fixed 256-bit key so a short dev secret such as
 * {@code test123} works while still allowing a strong production secret via
 * {@code JWT_SECRET}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(sha256(props.secret()));
        this.ttl = Duration.ofMinutes(props.expirationMinutes());
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies a token, returning the principal.
     *
     * @throws JwtException if the token is malformed, tampered, or expired
     */
    public AuthUser parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                c.getSubject(),
                c.get("email", String.class),
                c.get("name", String.class),
                Role.valueOf(c.get("role", String.class)));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
