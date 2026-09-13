package com.backlogtracker.ordertracker.master.service;

import java.security.SecureRandom;
import java.util.function.Function;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Generate-and-check numeric code assignment (design §5.1's feasibility note): pick a
 * random N-digit code, attempt to persist it under a unique index, and regenerate on a
 * collision. Reliable at this scale (3 digits = 1,000 values, comfortably enough for a
 * handful of creators/locations per business; collisions become likely only in the low
 * hundreds) without ever needing a human to pick a code by hand.
 */
@Component
public class RandomCodeAssigner {

    private static final int MAX_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();

    /** {@code save} should attempt to persist using the generated code and let a
     *  {@link DuplicateKeyException} propagate (via a unique index) on a collision. */
    public <T> T assign(int digits, Function<String, T> save) {
        int bound = (int) Math.pow(10, digits);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = String.format("%0" + digits + "d", random.nextInt(bound));
            try {
                return save.apply(code);
            } catch (DuplicateKeyException e) {
                // regenerate and retry
            }
        }
        throw new IllegalStateException(
                "Could not assign a unique " + digits + "-digit code after " + MAX_ATTEMPTS + " attempts");
    }
}
