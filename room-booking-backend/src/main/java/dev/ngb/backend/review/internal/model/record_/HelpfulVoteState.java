package dev.ngb.backend.review.internal.model.record_;

/**
 * Whether a helpfulness vote still counts.
 */
public enum HelpfulVoteState {
    /** It counts. */
    ACTIVE,
    /** The voter took it back. */
    WITHDRAWN
}
