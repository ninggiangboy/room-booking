package dev.ngb.backend.model;

/**
 * Where in the product an interaction happened.
 */
public enum DiscoverySurface {

    /** Search results. */
    SEARCH_RESULTS,

    /** Map. */
    MAP,

    /** Listing detail. */
    LISTING_DETAIL,

    /** Recommendation. */
    RECOMMENDATION,

    /** Saved listings. */
    SAVED_LISTINGS,

    /** Booking flow. */
    BOOKING_FLOW,

    /** Notification. */
    NOTIFICATION,

    /** Server. */
    SERVER,

    /** Unknown. */
    UNKNOWN
}
