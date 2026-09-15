package dev.ngb.backend.trust.internal.model.decision;

/**
 * How widely a policy version applies.
 */
public enum RiskPolicyScopeType {
    /** Everywhere; carries no scope identifier. */
    GLOBAL,
    /** One market. */
    MARKET,
    /** One host organization. */
    ORGANIZATION,
    /** One listing. */
    LISTING,
    /** A defined population segment. */
    SEGMENT
}
