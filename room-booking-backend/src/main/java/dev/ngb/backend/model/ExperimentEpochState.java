package dev.ngb.backend.model;

/**
 * Where one epoch stands.
 *
 * <p>Units are bucketed only while an epoch is RUNNING; stopping keeps the assignment and exposure
 * history rather than deleting unfavourable evidence.</p>
 */
public enum ExperimentEpochState {

    /** Being designed; variants and metrics may still be added. */
    DRAFT,

    /** Design accepted and frozen, not yet assigning. */
    APPROVED,

    /** The only state in which a unit may be bucketed. */
    RUNNING,

    /** Delivery suspended; assignments and exposures are kept. */
    PAUSED,

    /** Ended early. */
    STOPPED,

    /** Ran to its planned end. */
    COMPLETED
}
