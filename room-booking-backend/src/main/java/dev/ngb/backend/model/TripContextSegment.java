package dev.ngb.backend.model;

/**
 * A trip shape a preference may be held separately for.
 *
 * <p>A guest can be price-sensitive alone and less so with family, but a segment is only kept once
 * there is evidence for that segment in particular.</p>
 */
public enum TripContextSegment {

    /** Solo trip. */
    SOLO_TRIP,

    /** Family trip. */
    FAMILY_TRIP,

    /** Business trip. */
    BUSINESS_TRIP,

    /** Group trip. */
    GROUP_TRIP,

    /** Long stay. */
    LONG_STAY,

    /** Weekend. */
    WEEKEND
}
