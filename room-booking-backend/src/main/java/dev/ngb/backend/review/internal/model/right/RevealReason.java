package dev.ngb.backend.review.internal.model.right;

/**
 * Why a cycle revealed when it did.
 *
 * <p>Recorded so that a delayed reveal can be explained without telling either party what the other
 * did. The interface never exposes this.</p>
 */
public enum RevealReason {
    /** Both directions produced a qualified revision. */
    BOTH_SUBMITTED,
    /** The submission window closed. */
    DEADLINE_PASSED,
    /** Moderation held one side too long to keep the other waiting. */
    MAXIMUM_HOLD_REACHED,
    /** The policy in force does not seal at all. */
    POLICY_IMMEDIATE
}
