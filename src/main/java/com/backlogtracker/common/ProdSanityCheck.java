package com.backlogtracker.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.backlogtracker.security.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * Refuses to start the {@code prod} profile with unsafe dev defaults left in place.
 */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProdSanityCheck implements ApplicationRunner {

    private final JwtProperties jwt;

    @Override
    public void run(ApplicationArguments args) {
        if ("test123".equals(jwt.secret())) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the dev default in the prod profile — set a real secret");
        }
    }
}
