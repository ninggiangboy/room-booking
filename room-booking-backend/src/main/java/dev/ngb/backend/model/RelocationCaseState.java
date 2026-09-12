package dev.ngb.backend.model;

/**
 * Progress of moving a guest to different supply.
 */
public enum RelocationCaseState {
    /** Opened; nothing offered yet. */
    OPEN,
    /** One or more offers are with the guest. */
    OFFERING,
    /** An offer was taken and is being turned into a booking. */
    ACCEPTED,
    /** Finished, with an outcome recorded. */
    RESOLVED,
    /** Closed without a resolution being reached. */
    ABANDONED
}
