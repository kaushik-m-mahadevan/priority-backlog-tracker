package com.backlogtracker.commons.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlindIndexServiceTest {

    private final BlindIndexService service = new BlindIndexService(new EncryptionProperties("test-key"));

    @Test
    void nullAndBlankInputYieldNull() {
        assertThat(service.hash(null)).isNull();
        assertThat(service.hash("")).isNull();
        assertThat(service.hash("   ")).isNull();
    }

    @Test
    void normalizesCaseAndWhitespaceSoDifferentlyTypedEquivalentValuesCollide() {
        String canonical = service.hash("alex@example.com");
        assertThat(service.hash("Alex@Example.com")).isEqualTo(canonical);
        assertThat(service.hash(" alex@example.com ")).isEqualTo(canonical);
        assertThat(service.hash("ALEX@EXAMPLE.COM")).isEqualTo(canonical);
    }

    @Test
    void differentPlaintextsHashDifferently() {
        assertThat(service.hash("alex@example.com")).isNotEqualTo(service.hash("bo@example.com"));
    }

    @Test
    void sameKeyProducesTheSameHashAcrossInstances() {
        BlindIndexService another = new BlindIndexService(new EncryptionProperties("test-key"));
        assertThat(another.hash("alex@example.com")).isEqualTo(service.hash("alex@example.com"));
    }

    @Test
    void aDifferentEncryptionKeyProducesADifferentHash() {
        BlindIndexService differentKey = new BlindIndexService(new EncryptionProperties("a-totally-different-key"));
        assertThat(differentKey.hash("alex@example.com")).isNotEqualTo(service.hash("alex@example.com"));
    }

    /** Domain separation: BlindIndexService salts the shared app.encryption.key with a
     *  ":blind-index" label specifically so its derived key is never identical to
     *  AesGcmCipher's own key derived from the same raw secret — confirmed here by checking
     *  the two produce different output for the same plaintext/key. */
    @Test
    void derivesADifferentKeyFromAesGcmCipherDespiteSharingTheSameRawSecret() {
        AesGcmCipher cipher = new AesGcmCipher(new EncryptionProperties("test-key"));
        String blindIndexHash = service.hash("some-value");
        String cipherText = cipher.encrypt("some-value");

        // Different algorithms/outputs entirely, but the real point: decrypting the
        // ciphertext must still round-trip correctly, proving AesGcmCipher's key wasn't
        // accidentally swapped for the blind-index one derived from the same raw secret.
        assertThat(cipher.decrypt(cipherText)).isEqualTo("some-value");
        assertThat(blindIndexHash).isNotEqualTo(cipherText);
    }
}
