package dev.ngb.backend.util;

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
}
