package dev.ngb.backend.model;

/**
 * How a message is presented, which is the only thing about it that may still change.
 */
public enum MessageVisibility {
    /** Shown normally. */
    VISIBLE,
    /** Hidden from ordinary presentation; the protected evidence remains. */
    WITHDRAWN,
    /** Held by moderation pending a decision. */
    QUARANTINED,
    /** Shown as a policy-approved projection, with the original under restricted access. */
    MASKED
}
