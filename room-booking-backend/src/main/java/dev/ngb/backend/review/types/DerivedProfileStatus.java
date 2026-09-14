package dev.ngb.backend.review.types;

/**
 * Whether a derived profile is the one in force.
 *
 * <p>Used by every projection in this domain: quality, host reputation and aspect profiles.</p>
 */
public enum DerivedProfileStatus {
    /** The profile consumers should read. */
    CURRENT,
    /** Replaced by a newer computation. */
    SUPERSEDED,
    /** Being recomputed from source. */
    REBUILDING,
    /** The computation did not finish; the previous one still stands. */
    FAILED
}
