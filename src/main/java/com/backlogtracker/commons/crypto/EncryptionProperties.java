package com.backlogtracker.commons.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.encryption.key}. Same shape as {@code app.jwt.secret} — any string is
 * accepted (the dev default is short and obviously fake), stretched to a 256-bit AES key
 * by {@link AesGcmCipher}, so a strong generated production secret is a drop-in
 * replacement with no code change.
 */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String key) {

    public EncryptionProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("app.encryption.key must be set");
        }
    }
}
