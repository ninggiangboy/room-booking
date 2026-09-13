package dev.ngb.backend.model;

/**
 * Whether a recorded correction has been carried out yet.
 */
public enum CorrectionApplicationState {

    /** Recorded but not yet carried out. */
    PENDING,

    /** Carried out. */
    APPLIED,

    /** Could not be carried out. */
    FAILED
}
