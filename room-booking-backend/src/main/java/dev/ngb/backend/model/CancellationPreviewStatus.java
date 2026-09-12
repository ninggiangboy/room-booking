package dev.ngb.backend.model;

/**
 * Whether a quoted cancellation figure is still usable.
 *
 * <p>A preview is a promise with a deadline. Once redeemed it cannot found a second decision, and once
 * expired it cannot be redeemed at all, because the facts it was computed from have moved.</p>
 */
public enum CancellationPreviewStatus {
    /** Still within its window and not yet redeemed. */
    ACTIVE,
    /** A decision was committed against it. */
    CONSUMED,
    /** Its window closed unused. */
    EXPIRED,
    /** A newer preview replaced it before it was used. */
    SUPERSEDED
}
