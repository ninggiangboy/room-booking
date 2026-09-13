package dev.ngb.backend.model;

/**
 * How an emergency grant stopped granting anything.
 */
public enum BreakGlassClosureReason {

    /** Ran to its expiry without being handed back. */
    EXPIRED,

    /** Handed back by the operator once the emergency ended. */
    SURRENDERED,

    /** Taken away before it expired. */
    REVOKED
}
