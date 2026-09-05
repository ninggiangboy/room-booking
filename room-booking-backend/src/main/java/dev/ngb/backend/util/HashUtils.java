package dev.ngb.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stateless helpers for producing deterministic cryptographic hashes.
 *
 * <p>Authentication services store token hashes rather than raw bearer secrets. The private
 * constructor prevents this static-only utility from being instantiated.</p>
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Hashes UTF-8 text with SHA-256 and returns its lower-case hexadecimal representation.
     *
     * @param value text to hash
     * @return 64-character SHA-256 hexadecimal digest
     * @throws NullPointerException when {@code value} is {@code null}
     */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
