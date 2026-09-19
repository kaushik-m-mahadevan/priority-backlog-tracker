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
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return save.apply(randomDigits(digits));
            } catch (DuplicateKeyException e) {
                // regenerate and retry
            }
        }
        throw new IllegalStateException(
                "Could not assign a unique " + digits + "-digit code after " + MAX_ATTEMPTS + " attempts");
    }

    /** The bare random-digit generation {@link #assign} uses internally, exposed for a
     *  caller that needs its own uniqueness rule instead of a DB unique-index check — e.g.
     *  two codes that must merely differ from each other, not from every other document's
     *  (see {@code BusinessConfigService.seed}'s individual/bulk order-type codes). */
    public String randomDigits(int digits) {
        int bound = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", random.nextInt(bound));
    }
}
