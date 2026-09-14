package dev.ngb.backend.discovery.internal.model.event;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.supply.internal.model.listing.Listing;

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
