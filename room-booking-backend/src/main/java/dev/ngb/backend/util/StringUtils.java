package dev.ngb.backend.util;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

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
    public static @Nullable String normalize(@Nullable String value) {
        return value == null ? null : value.strip();
    }

    /**
     * Normalizes required text and converts it to lower case using a stable, language-neutral
     * locale.
     *
     * @param value non-null input text
     * @return normalized lower-case text
     */
    public static String normalizeLowerCase(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes optional text, collapsing absent and whitespace-only values to {@code null}.
     *
     * <p>Optional columns store {@code null} rather than an empty string so that "not provided"
     * has a single representation in the database.</p>
     *
     * @param value input text, possibly {@code null}
     * @return stripped text, or {@code null} when the input is {@code null} or blank
     */
    public static @Nullable String normalizeOptional(@Nullable String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /**
     * Removes leading and trailing Unicode whitespace from required text.
     *
     * @param value non-null input text
     * @return stripped text
     */
    public static String normalizeRequired(String value) {
        return value.strip();
    }
}
