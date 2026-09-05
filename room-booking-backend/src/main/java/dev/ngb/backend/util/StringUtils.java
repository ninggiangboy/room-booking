package dev.ngb.backend.util;

import java.util.Locale;

/**
 * Common, null-safe string normalization operations.
 *
 * <p>The private constructor prevents accidental instantiation: this utility has no object state
 * and exposes only static functions.</p>
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Removes leading and trailing Unicode whitespace while preserving {@code null}.
     *
     * @param value input text, possibly {@code null}
     * @return stripped text, or {@code null} when the input is {@code null}
     */
    public static String normalize(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * Normalizes text and converts it to lower case using a stable, language-neutral locale.
     *
     * @param value input text, possibly {@code null}
     * @return normalized lower-case text, or {@code null}
     */
    public static String normalizeLowerCase(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
