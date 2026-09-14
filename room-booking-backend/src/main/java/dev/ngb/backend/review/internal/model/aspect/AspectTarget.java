package dev.ngb.backend.review.internal.model.aspect;

/**
 * What an aspect mention is about.
 */
public enum AspectTarget {
    /** The place itself. */
    LISTING,
    /** How the host behaved. */
    HOST_SERVICE,
    /** How the guest behaved. */
    GUEST_CONDUCT,
    /** The platform experience. */
    PLATFORM,
    /** The neighbourhood rather than the property. */
    AREA
}
