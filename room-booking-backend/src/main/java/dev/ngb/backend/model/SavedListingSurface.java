package dev.ngb.backend.model;

/**
 * Where a guest was when they saved a listing.
 */
public enum SavedListingSurface {

    /** Search results. */
    SEARCH_RESULTS,

    /** Listing detail. */
    LISTING_DETAIL,

    /** Map. */
    MAP,

    /** Recommendation. */
    RECOMMENDATION,

    /** Booking flow. */
    BOOKING_FLOW,

    /** Import. */
    IMPORT,

    /** Unknown. */
    UNKNOWN
}
