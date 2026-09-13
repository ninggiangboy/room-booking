package dev.ngb.backend.model;

/**
 * How an appeal against the decision behind an outcome was resolved.
 */
public enum LabelAppealOutcome {

    /** The original decision stood. */
    UPHELD,

    /** The original decision was overturned. */
    REVERSED,

    /** Part of the original decision was overturned. */
    PARTIALLY_REVERSED,

    /** The appeal was withdrawn before it was decided. */
    WITHDRAWN
}
