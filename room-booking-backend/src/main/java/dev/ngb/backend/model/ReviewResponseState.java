package dev.ngb.backend.model;

/**
 * Where a host response stands.
 */
public enum ReviewResponseState {
    /** Being written. */
    DRAFT,
    /** Submitted, with its text in a revision. */
    SUBMITTED,
    /** Taken back by the responder. */
    WITHDRAWN,
    /** The response window closed unused. */
    EXPIRED
}
