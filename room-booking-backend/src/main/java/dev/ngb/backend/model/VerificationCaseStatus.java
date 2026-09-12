package dev.ngb.backend.model;

/**
 * Where a verification case currently sits.
 *
 * <p>{@link #MANUAL_REVIEW} is a first-class state rather than an error path: automated verification
 * is expected to be inconclusive sometimes, and a host must not be permanently blocked because a
 * vendor could not read their document.</p>
 */
public enum VerificationCaseStatus {
    /** Opened and being prepared. */
    OPEN,
    /** Waiting for the host to supply something. */
    AWAITING_INPUT,
    /** Submitted to a provider and awaiting its verdict. */
    AWAITING_PROVIDER,
    /** Escalated to a person because automation could not conclude. */
    MANUAL_REVIEW,
    /** Concluded, with an outcome recorded. */
    COMPLETED,
    /** Given up on without a verdict. */
    ABANDONED
}
