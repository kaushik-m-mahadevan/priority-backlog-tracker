package com.backlogtracker.commons.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared SHA-256 digest helper — {@link AesGcmCipher}, {@link BlindIndexService}, and
 *  {@code JwtService} each derive a key from a configured secret this same way; this was
 *  previously three byte-identical private copies. */
public final class Sha256 {

    private Sha256() {
    }

    public static byte[] digest(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
