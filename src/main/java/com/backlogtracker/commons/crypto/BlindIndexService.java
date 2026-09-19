package com.backlogtracker.commons.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Deterministic HMAC-SHA256 "blind index" for exact-match lookups on fields that are
 * otherwise stored as {@link EncryptedString} (semantically-secure AES-GCM, so the same
 * plaintext never produces the same ciphertext twice — the whole point, but it also means
 * ciphertext can never be queried). A blind index trades a little of that away on purpose,
 * for one field at a time, wherever a feature genuinely needs to look a record up by that
 * field's value (e.g. "does this email already belong to a customer?").
 *
 * <p>The index only ever reveals equality — same input always hashes to the same output —
 * never the plaintext itself; that's the standard blind-indexing trade-off. Values are
 * normalized (trimmed, lower-cased) before hashing so "Alex@Example.com" and
 * " alex@example.com " collide to the same index.
 *
 * <p>Reuses {@code app.encryption.key} with a distinct domain-separation label rather than
 * introducing a second required config property — same "any string works, gets hashed to a
 * fixed-length key" pattern as {@link AesGcmCipher} and {@code JwtService}, just salted
 * differently so the HMAC key is never identical to the AES key.
 */
@Component
public class BlindIndexService {

    private final SecretKeySpec key;

    public BlindIndexService(EncryptionProperties props) {
        this.key = new SecretKeySpec(Sha256.digest(props.key() + ":blind-index"), "HmacSHA256");
    }

    /** Normalizes then hashes; null/blank input yields null (nothing to index). */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String normalized = plaintext.trim().toLowerCase();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getEncoder().encodeToString(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Blind-index hashing failed", e);
        }
    }
}
