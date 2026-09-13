package dev.ngb.backend.model;

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
