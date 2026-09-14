package dev.ngb.backend.review.internal.model.record_;

/**
 * What a category rating is about.
 */
public enum ReviewCategoryTarget {
    /** The place itself. */
    LISTING,
    /** How the host behaved. */
    HOST_SERVICE,
    /** How the guest behaved. */
    GUEST_CONDUCT,
    /** The platform experience. */
    PLATFORM
}
