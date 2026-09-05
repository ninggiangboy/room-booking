package dev.ngb.backend.util;

import java.time.Duration;

/**
 * Common validation for externally configured durations.
 *
 * <p>The private constructor prevents instantiation because this class holds no object state.
 * Token services reuse this helper to fail application startup for invalid lifetimes.</p>
 */
public final class DurationUtils {

    private DurationUtils() {
    }

    /**
     * Returns a duration when it is present and greater than zero.
     *
     * @param duration configured duration
     * @param name configuration name used in the validation message
     * @return the validated duration
     * @throws IllegalArgumentException when the duration is null, zero, or negative
     */
    public static Duration requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
