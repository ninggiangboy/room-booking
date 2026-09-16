package dev.ngb.backend.platform.internal.service.secret;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.ngb.backend.platform.SecretBox;

/**
 * AES-256-GCM implementation of the application's secret-sealing port.
 *
 * <p>{@code @Component} registers this implementation as an injectable bean, matching
 * {@code SmtpEmailSender}'s structure. Unlike {@code LoggingSmsSender}, this is a real, working
 * implementation rather than an interim placeholder: encryption at rest needs no third-party
 * account the way sending an SMS does, so there is no reason to defer it. {@code keyVersion} is
 * always {@code 1} today — this class holds exactly one configured key — so introducing a second
 * key (rotation) is future work that must teach {@link #open} to select among versions; it is not
 * a configuration change alone.</p>
 */
@Component
class AesGcmSecretBox implements SecretBox {

    private static final short KEY_VERSION = 1;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Builds the box from an externally configured Base64-encoded AES-256 key.
     *
     * @param base64Key Base64-encoded key containing exactly 256 bits
     */
    AesGcmSecretBox(@Value("${app.secret-box.key}") @Nullable String base64Key) {
        this.key = new SecretKeySpec(decodeKey(base64Key), "AES");
    }

    /**
     * Seals plaintext with a fresh random nonce, prefixing it to the ciphertext.
     *
     * @param plaintext material to seal
     * @return a Base64 reference of {@code nonce || ciphertext || tag}, and key version 1
     */
    @Override
    public SealedSecret seal(byte[] plaintext) {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] sealed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, sealed, 0, iv.length);
            System.arraycopy(ciphertext, 0, sealed, iv.length, ciphertext.length);
            return new SealedSecret(Base64.getEncoder().encodeToString(sealed), KEY_VERSION);
        } catch (Exception exception) {
            // Every checked exception Cipher can throw here reflects a configuration mistake
            // (bad key, bad algorithm), not a runtime condition a caller could recover from.
            throw new IllegalStateException("could not seal secret", exception);
        }
    }

    /**
     * Recovers plaintext previously sealed by {@link #seal}.
     *
     * @param reference Base64 reference produced by {@link #seal}
     * @param keyVersion must be {@code 1}, the only version this box holds
     * @return the original plaintext
     * @throws IllegalStateException when {@code keyVersion} names a key this box does not hold
     */
    @Override
    public byte[] open(String reference, short keyVersion) {
        if (keyVersion != KEY_VERSION) {
            throw new IllegalStateException("unknown secret-box key version: " + keyVersion);
        }
        byte[] sealed = Base64.getDecoder().decode(reference);
        byte[] iv = Arrays.copyOfRange(sealed, 0, GCM_IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(sealed, GCM_IV_LENGTH_BYTES, sealed.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("could not open sealed secret", exception);
        }
    }

    private static byte[] decodeKey(@Nullable String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("app.secret-box.key must not be blank");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("app.secret-box.key must be valid Base64", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "app.secret-box.key must decode to exactly 256 bits (32 bytes), was "
                            + keyBytes.length * 8 + " bits");
        }
        return keyBytes;
    }
}
