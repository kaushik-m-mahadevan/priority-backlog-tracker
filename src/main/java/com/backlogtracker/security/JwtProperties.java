package com.backlogtracker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.jwt.*}. The secret is any string (the dev default is short); it is
 * stretched to a 256-bit HMAC key by {@link JwtService}, so a stronger production secret
 * is a drop-in replacement.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be set");
        }
        if (expirationMinutes <= 0) {
            expirationMinutes = 1440;
        }
    }
}
