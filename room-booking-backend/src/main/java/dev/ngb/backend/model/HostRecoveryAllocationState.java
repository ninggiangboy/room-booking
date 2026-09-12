package dev.ngb.backend.model;

/**
 * Whether one waterfall step actually took effect.
 */
public enum HostRecoveryAllocationState {
    /** Chosen by the waterfall and not yet applied. */
    PLANNED,
    /** Consumed the amount it names. */
    APPLIED,
    /** Undone, typically because the debt itself was reversed. */
    REVERSED,
    /** Could not be applied; a later step or a person takes over. */
    FAILED
}
