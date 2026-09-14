package dev.ngb.backend.growth.internal.model.program;

/**
 * Where a growth programme stands in its own lifecycle.
 */
public enum GrowthProgramStatus {

    /** Still being written and not yet binding on anybody. */
    DRAFT,

    /** Open and accepting participants. */
    ACTIVE,

    /** Temporarily not accepting anybody new, without being closed. */
    PAUSED,

    /** Finished; existing obligations are still honoured. */
    CLOSED
}
