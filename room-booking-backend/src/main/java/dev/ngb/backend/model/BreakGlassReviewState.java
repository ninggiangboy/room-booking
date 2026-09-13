package dev.ngb.backend.model;

/**
 * Where the post-use review of an emergency grant stands. An unreviewed expired grant is the audit
 * finding, which is why the state is tracked rather than inferred.
 */
public enum BreakGlassReviewState {

    /** The grant has been used or has expired and nobody has looked at it yet. */
    PENDING,

    /** Somebody is looking at what was done under it. */
    IN_REVIEW,

    /** Reviewed, with a finding recorded. */
    REVIEWED,

    /** Reviewed and raised further, because what was found needs more than a note. */
    ESCALATED
}
