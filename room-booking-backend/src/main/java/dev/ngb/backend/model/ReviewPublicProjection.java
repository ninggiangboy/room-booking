package dev.ngb.backend.model;

/**
 * What the public can currently see.
 *
 * <p>Derived from the other dimensions rather than set directly, and never public before the cycle
 * revealed -- a check constraint says so.</p>
 */
public enum ReviewPublicProjection {
    /** Nothing is shown. */
    NOT_PUBLIC,
    /** Shown as written. */
    PUBLIC,
    /** Shown with parts removed. */
    PUBLIC_REDACTED,
    /** Was shown and is not any more. */
    HIDDEN
}
