package com.backlogtracker.commons.crypto;

import java.util.Objects;

/**
 * Wrapper for a String field that must be encrypted at rest (design: Order Tracker §4.5 —
 * PII and financial fields, application-layer AES-256-GCM, zero paid-tier infrastructure
 * required). Spring Data converters only register per-type, not per-field-name, so this
 * wrapper is what lets a domain class mark specific fields as "encrypt this one" while
 * leaving ordinary {@code String} fields on the same document alone — see
 * {@link AesGcmCipher}, {@link EncryptedStringWritingConverter}, and
 * {@link EncryptedStringReadingConverter}.
 *
 * <p>{@code toString()} is deliberately redacted — logging or otherwise stringifying a
 * domain object must never leak the plaintext by accident.
 */
public final class EncryptedString {

    private final String value;

    private EncryptedString(String value) {
        this.value = value;
    }

    public static EncryptedString of(String plaintext) {
        return plaintext == null ? null : new EncryptedString(plaintext);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptedString other && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "EncryptedString[REDACTED]";
    }
}
