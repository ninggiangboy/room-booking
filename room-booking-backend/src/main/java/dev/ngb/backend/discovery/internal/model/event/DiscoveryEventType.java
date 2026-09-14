package dev.ngb.backend.discovery.internal.model.event;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * The behavioural events discovery ingests.
 *
 * <p>Until migration 030 delivers the platform event registry, this vocabulary is the contract.</p>
 */
public enum DiscoveryEventType {

    /** Search submitted. */
    SEARCH_SUBMITTED,

    /** Search results returned. */
    SEARCH_RESULTS_RETURNED,

    /** Listing impression. */
    LISTING_IMPRESSION,

    /** Listing clicked. */
    LISTING_CLICKED,

    /** Listing viewed. */
    LISTING_VIEWED,

    /** Map marker selected. */
    MAP_MARKER_SELECTED,

    /** Listing favorited. */
    LISTING_FAVORITED,

    /** Listing unfavorited. */
    LISTING_UNFAVORITED,

    /** Booking started. */
    BOOKING_STARTED,

    /** Booking created. */
    BOOKING_CREATED,

    /** Booking confirmed. */
    BOOKING_CONFIRMED,

    /** Booking cancelled. */
    BOOKING_CANCELLED,

    /** Stay completed. */
    STAY_COMPLETED,

    /** Review submitted. */
    REVIEW_SUBMITTED,

    /** Review published. */
    REVIEW_PUBLISHED
}
