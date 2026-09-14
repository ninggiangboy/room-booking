package dev.ngb.backend.support.internal.model.decision;

/**
 * The requested outcome of {@code case_appeals}.
 */
public enum AppealRequestedOutcome {

    /** Reverse. */
    REVERSE,

    /** Increase remedy. */
    INCREASE_REMEDY,

    /** Reclassify. */
    RECLASSIFY,

    /** Reopen investigation. */
    REOPEN_INVESTIGATION,

    /** Correct record. */
    CORRECT_RECORD
}
