package dev.ngb.backend.discovery.internal.model.personalization;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.supply.internal.model.listing.Listing;

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
