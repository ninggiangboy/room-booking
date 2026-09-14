package dev.ngb.backend.support.internal.model;

/**
 * How much independent approval an action needs before it may proceed.
 */
public enum ApprovalTier {

    /** None. */
    NONE,

    /** Peer. */
    PEER,

    /** Supervisor. */
    SUPERVISOR,

    /** Specialist. */
    SPECIALIST,

    /** Legal. */
    LEGAL
}
