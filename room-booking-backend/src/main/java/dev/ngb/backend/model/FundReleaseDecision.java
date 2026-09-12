package dev.ngb.backend.model;

/**
 * The answer a release evaluation produced.
 *
 * <p>Recorded every time the question is asked, not only when the answer is yes, so a host who asks
 * why their money is not available gets reason codes and input versions rather than an amount that
 * quietly vanished from the balance.</p>
 */
public enum FundReleaseDecision {
    /** The predicates passed and the amount became available. */
    RELEASED,
    /** Not yet; a condition is still pending and will be re-evaluated. */
    DEFERRED,
    /** Stopped by an applicable hold. */
    HELD,
    /** Refused until something changes outside the release policy. */
    BLOCKED
}
