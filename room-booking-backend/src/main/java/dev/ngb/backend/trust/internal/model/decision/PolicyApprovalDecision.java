package dev.ngb.backend.trust.internal.model.decision;

/**
 * What an approver said.
 *
 * <p>A rejection is not merely advisory: the activation guard refuses to make a policy active while
 * any rejection stands against it.</p>
 */
public enum PolicyApprovalDecision {
    /** Approved for activation. */
    APPROVED,
    /** Refused, with a rationale. */
    REJECTED,
    /** Declined to decide. */
    ABSTAINED
}
