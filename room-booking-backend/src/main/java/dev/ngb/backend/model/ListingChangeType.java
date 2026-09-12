package dev.ngb.backend.model;

/**
 * Kind of change recorded in a listing's history.
 *
 * <p>The history exists for disputes: a guest arguing "the listing said there was air conditioning"
 * needs the listing as it was on the day they booked, not as it is now.</p>
 */
public enum ListingChangeType {
    /** The listing was created. */
    CREATED,
    /** Title, summary, or description changed. */
    CONTENT_EDITED,
    /** Photographs or other media changed. */
    MEDIA_CHANGED,
    /** Claimed amenities changed. */
    AMENITIES_CHANGED,
    /** House rules changed. */
    RULES_CHANGED,
    /** The listing became publicly bookable. */
    PUBLISHED,
    /** The listing was withdrawn from sale. */
    PAUSED,
    /** The listing was permanently withdrawn. */
    ARCHIVED,
    /** A moderator acted on the listing's content. */
    MODERATED,
    /** The owning account holder or collaborators changed. */
    OWNERSHIP_CHANGED
}
