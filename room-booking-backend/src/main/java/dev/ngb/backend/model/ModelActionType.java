package dev.ngb.backend.model;

/**
 * What a command against a model version or its route asks for.
 */
public enum ModelActionType {

    /** Record that the required approvals are in place. */
    APPROVE,

    /** Give a version more traffic or a wider scope. */
    PROMOTE,

    /** Stop a version serving without changing its registration. */
    PAUSE,

    /** Return routing to the previous approved version or the deterministic fallback. */
    ROLLBACK,

    /** Withdraw a version permanently. */
    RETIRE,

    /** Refuse a version at review, permanently. */
    REJECT
}
