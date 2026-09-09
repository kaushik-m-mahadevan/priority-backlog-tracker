package com.backlogtracker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.backlogtracker.user.domain.User;

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
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies a token and returns its subject (the user id). Role and status are read
     * fresh from the database by the auth filter, never trusted from the token.
     *
     * @throws JwtException if the token is malformed, tampered, or expired
     */
    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
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
