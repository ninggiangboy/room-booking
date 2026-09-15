package dev.ngb.backend.trust.internal.model;

/**
 * The bounded answer one evaluation produces.
 *
 * <p>This is the whole vocabulary. A protected action narrows the set it will accept and can never
 * extend it, which is checked by trigger when a decision is written: a generic endpoint cannot be
 * talked into an outcome its action never registered.</p>
 */
public enum RiskDecisionOutcome {
    /** No policy reason to block this exact action now; the domain still checks its own invariants. */
    ALLOW,
    /** More proof is required before the action may commit. */
    CHALLENGE,
    /** One named eligibility dimension is stopped until a stated instant. */
    HOLD,
    /** An authorized person must decide. */
    MANUAL_REVIEW,
    /** Permitted only within a stated scope, rate or amount. */
    LIMIT,
    /** Prohibited under the applied policy, with a stable reason family and an appeal path. */
    DENY
}
