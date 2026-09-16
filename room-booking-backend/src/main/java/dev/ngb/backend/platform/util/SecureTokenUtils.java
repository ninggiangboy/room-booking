package dev.ngb.backend.platform.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically secure opaque token values.
 *
 * <p>A shared {@link SecureRandom} supplies unpredictable bytes. URL-safe unpadded Base64 allows a
 * token to travel in links without exposing internal structure. The private constructor prevents
 * instantiation of this static-only utility.</p>
 */
public final class SecureTokenUtils {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokenUtils() {
    }

    /**
     * Generates an unpadded URL-safe Base64 token containing 256 random bits.
     *
     * @return 43-character URL-safe opaque token representing 32 random bytes
     */
    public static String generateUrlSafe() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates a fixed-width numeric one-time code, left-padded with zeros.
     *
     * <p>A code is used, rather than {@link #generateUrlSafe()}'s opaque token, wherever the value
     * must be typed back by hand instead of followed as a link — an SMS-delivered contact-channel
     * verification code, for instance. {@link SecureRandom#nextInt(int)} is uniform over its bound,
     * so every digit position is uniform once the result is left-padded.</p>
     *
     * @param digits width of the code; must be positive
     * @return numeric string of exactly {@code digits} characters, {@code '0'}-{@code '9'} only
     */
    public static String generateNumericCode(int digits) {
        if (digits <= 0) {
            throw new IllegalArgumentException("digits must be positive");
        }
        int bound = (int) Math.pow(10, digits);
        int value = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + digits + "d", value);
    }
}
