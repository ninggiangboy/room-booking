package dev.ngb.backend.model;

/**
 * Whether an intervention can be, or has been, appealed.
 */
public enum ModerationAppealState {
    /** No appeal route applies. */
    NOT_APPEALABLE,
    /** An appeal may be requested. */
    AVAILABLE,
    /** An appeal is open. */
    REQUESTED,
    /** The original decision stands. */
    UPHELD,
    /** The decision was reversed. */
    OVERTURNED
}
