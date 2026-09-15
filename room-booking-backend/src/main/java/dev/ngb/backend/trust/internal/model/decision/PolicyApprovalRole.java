package dev.ngb.backend.trust.internal.model.decision;

/**
 * Which authority an approver signed under.
 */
public enum PolicyApprovalRole {
    /** The team that owns the policy. */
    POLICY_OWNER,
    /** Risk governance. */
    RISK_GOVERNANCE,
    /** Legal or compliance review. */
    LEGAL,
    /** Safety review. */
    SAFETY,
    /** Engineering review of the rule document. */
    ENGINEERING
}
