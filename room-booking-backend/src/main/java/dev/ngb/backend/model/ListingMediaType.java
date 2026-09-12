package dev.ngb.backend.model;

/**
 * Kind of media attached to a listing.
 *
 * <p>Separated by type because they are governed differently: a floor plan is not decorative, and a
 * virtual tour has delivery and moderation characteristics a still image does not.</p>
 */
public enum ListingMediaType {
    /** A still photograph. */
    IMAGE,
    /** A video clip. */
    VIDEO,
    /** A floor plan or layout diagram. */
    FLOOR_PLAN,
    /** An interactive or panoramic tour. */
    TOUR
}
