package dev.ngb.backend.model;

/**
 * Whether a mention counts towards the profiles.
 */
public enum MentionInclusionState {
    /** It counts. */
    QUALIFIED,
    /** It does not, for example because its review was removed. */
    EXCLUDED,
    /** A later extraction replaced it. */
    SUPERSEDED,
    /** Held for a human to look at. */
    PENDING_REVIEW
}
