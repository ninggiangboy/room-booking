package dev.ngb.backend.model;

/**
 * Whether a listing is publicly visible and bookable.
 *
 * <p>{@link #PAUSED} is separated from {@link #ARCHIVED} because a host taking a listing down for a
 * season and one retiring it for good need different outcomes for their existing bookings and their
 * search history.</p>
 *
 * <p>At most one listing per accommodation type may sit in a live state, since two published
 * listings for the same sellable inventory would compete for the same nights.</p>
 */
public enum ListingStatus {
    /** Being written; not visible. */
    DRAFT,
    /** Submitted and awaiting content moderation. */
    IN_REVIEW,
    /** Publicly visible and bookable. */
    PUBLISHED,
    /** Withdrawn from sale but retained and restorable. */
    PAUSED,
    /** Permanently withdrawn; retained for history. */
    ARCHIVED
}
