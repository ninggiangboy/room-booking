package dev.ngb.backend.model;

/**
 * Whether a helpfulness vote still counts.
 */
public enum HelpfulVoteState {
    /** It counts. */
    ACTIVE,
    /** The voter took it back. */
    WITHDRAWN
}
