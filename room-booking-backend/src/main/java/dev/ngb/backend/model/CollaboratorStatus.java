package dev.ngb.backend.model;

/**
 * Whether a property collaboration is currently in force.
 *
 * <p>Ended collaborations are retained so the actions taken during them stay attributable.</p>
 */
public enum CollaboratorStatus {
    /** Currently collaborating. */
    ACTIVE,
    /** Temporarily barred; authority should not evaluate. */
    SUSPENDED,
    /** No longer collaborating; retained for history. */
    ENDED
}
