package dev.ngb.backend.model;

/**
 * The ordering a guest asked for.
 *
 * <p>Everything other than {@code RECOMMENDED} bypasses the recommendation order while still applying
 * every eligibility constraint.</p>
 */
public enum DiscoverySortMode {

    /** Recommended. */
    RECOMMENDED,

    /** Price low to high. */
    PRICE_LOW_TO_HIGH,

    /** Rating high to low. */
    RATING_HIGH_TO_LOW,

    /** Distance. */
    DISTANCE,

    /** Newest. */
    NEWEST
}
