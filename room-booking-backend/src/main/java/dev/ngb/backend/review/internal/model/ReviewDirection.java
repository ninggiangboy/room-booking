package dev.ngb.backend.review.internal.model;

/**
 * Who is reviewing whom.
 */
public enum ReviewDirection {
    /** The guest on the listing experience and the host service attributed to it. */
    GUEST_TO_LISTING,
    /** The host, or an authorized representative, on guest conduct. */
    HOST_TO_GUEST
}
