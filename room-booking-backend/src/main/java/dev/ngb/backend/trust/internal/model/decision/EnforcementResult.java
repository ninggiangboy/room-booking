package dev.ngb.backend.trust.internal.model.decision;

/**
 * What the authoritative domain actually did with a decision.
 *
 * <p>Checked against the decision by trigger. A command cannot record that it proceeded on a
 * decision that denied, and anything other than carrying the decision out must be recorded as a
 * divergence with a stated reason.</p>
 */
public enum EnforcementResult {
    /** The command went ahead. */
    PROCEEDED,
    /** The command was refused. */
    BLOCKED,
    /** The command waited for a challenge. */
    CHALLENGED,
    /** The command was held pending a deadline or a review. */
    HELD,
    /** The command proceeded inside the stated limit. */
    LIMITED,
    /** Facts had changed and a new decision was asked for. */
    REEVALUATION_REQUESTED,
    /** Something other than the decision happened, with a reason. */
    DIVERGED;
}
