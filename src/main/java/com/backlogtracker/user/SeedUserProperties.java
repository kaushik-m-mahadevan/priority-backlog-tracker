package com.backlogtracker.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.seed-user.*} — the placeholder dev account (no real founders yet). */
@ConfigurationProperties(prefix = "app.seed-user")
public record SeedUserProperties(
        boolean enabled,
        String email,
        String password,
        String name,
        String userCode) {
}
