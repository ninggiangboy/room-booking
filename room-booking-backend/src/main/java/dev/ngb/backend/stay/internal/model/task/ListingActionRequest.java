package dev.ngb.backend.stay.internal.model.task;

/**
 * What a maintenance record asks of listing publication.
 */
public enum ListingActionRequest {
    /** Nothing. */
    NONE,
    /** Take the listing off sale. */
    PAUSE_PUBLICATION,
    /** Correct what the listing claims. */
    CONTENT_CORRECTION
}
