package dev.ngb.backend.review.internal.model.right;

/**
 * Where a bounded permission to review stands.
 *
 * <p>A right is not reopened because an author withdrew or moderation removed the text. Revocation
 * needs a superseding booking fact, not a support preference.</p>
 */
public enum ReviewRightState {
    /** Waiting on a verified completion fact. */
    PENDING_SOURCE,
    /** Exercisable until the deadline. */
    OPEN,
    /** It produced one review aggregate; edits happen as revisions within it. */
    EXERCISED,
    /** The deadline passed unused. */
    EXPIRED,
    /** A superseding booking fact proved the eligibility basis was invalid. */
    REVOKED
}
