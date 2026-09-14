package dev.ngb.backend.trust.internal.model.decision;

/**
 * Where a policy version stands in its release.
 *
 * <p>Activation is guarded: a rejection blocks it outright, a tier-three or tier-four policy needs
 * two approvers who are not its author, and once active the version is frozen and its effective
 * window may be closed but never extended.</p>
 */
public enum RiskPolicyStatus {
    /** Being written. */
    DRAFT,
    /** Approved but not yet in force. */
    APPROVED,
    /** In force. */
    ACTIVE,
    /** Temporarily not applied; history unchanged. */
    SUSPENDED,
    /** Replaced by a later version. */
    SUPERSEDED,
    /** Withdrawn. */
    RETIRED;
}
