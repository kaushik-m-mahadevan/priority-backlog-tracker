package com.backlogtracker.commons.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

    private final AesGcmCipher cipher = new AesGcmCipher(new EncryptionProperties("unit-test-key"));

    @Test
    void encryptsAndDecryptsBackToTheOriginal() {
        String plaintext = "Priya Nair, +91 98765 43210";
        String stored = cipher.encrypt(plaintext);

        assertThat(stored).isNotEqualTo(plaintext);
        assertThat(cipher.decrypt(stored)).isEqualTo(plaintext);
    }

    @Test
    void twoEncryptionsOfTheSameValueDifferBecauseTheNonceIsRandom() {
        String a = cipher.encrypt("same value");
        String b = cipher.encrypt("same value");

        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("same value");
        assertThat(cipher.decrypt(b)).isEqualTo("same value");
    }

    @Test
    void decryptingWithTheWrongKeyFails() {
        String stored = cipher.encrypt("secret");
        AesGcmCipher wrongKey = new AesGcmCipher(new EncryptionProperties("a different key"));

        assertThatThrownBy(() -> wrongKey.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
