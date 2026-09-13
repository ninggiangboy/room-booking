package dev.ngb.backend.model;

/**
 * The result state of {@code remedy_instructions}.
 */
public enum InstructionResultState {

    /** Accepted. */
    ACCEPTED,

    /** Rejected. */
    REJECTED,

    /** Pending. */
    PENDING,

    /** Unknown. */
    UNKNOWN,

    /** Committed. */
    COMMITTED,

    /** Partial. */
    PARTIAL
}
