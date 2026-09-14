package dev.ngb.backend.trust.internal.model;

/**
 * How much a protected action can cost if it goes wrong.
 *
 * <p>The tier sets the latency budget, the failure mode and the evidence a decision needs. The
 * database enforces the two ends of the range: an urgent safety action may never fail open and
 * always routes to a human, and a tier-three or tier-four policy needs two approvers before it can
 * be activated.</p>
 */
public enum RiskActionTier {
    /** Informational, reversible, monitored asynchronously. */
    T0,
    /** Reversible, such as sending a message or saving a draft. */
    T1,
    /** Scarce or contractual, such as publishing a listing or confirming a booking. */
    T2,
    /** Money or privilege, such as a capture, refund or payout destination change. */
    T3,
    /** Urgent safety, which never silently fails. */
    T4;
}
