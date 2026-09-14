package com.backlogtracker.commons;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.backlogtracker.commons.crypto.EncryptionProperties;
import com.backlogtracker.commons.security.JwtProperties;

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
    private final EncryptionProperties encryption;

    @Override
    public void run(ApplicationArguments args) {
        if ("test123".equals(jwt.secret())) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the dev default in the prod profile — set a real secret");
        }
        // Unlike the JWT secret (which only lets someone forge a login), this key is what
        // every EncryptedString field and blind-index hash in the database is derived
        // from — if ENCRYPTION_KEY is ever left unset, the app would otherwise start
        // normally and silently decrypt/re-encrypt everything under a key that's
        // committed in plaintext in this repo.
        if ("dev-only-not-a-real-key".equals(encryption.key())) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is still the dev default in the prod profile — set a real secret");
        }
    }
}
