package dev.ngb.backend.trust.internal.model.decision;

/**
 * Who took a decision.
 */
public enum RiskDecider {
    /** Policy evaluation with no person involved. */
    AUTOMATION,
    /** An authorized reviewer. */
    REVIEWER,
    /** An appeal reviewer. */
    APPEAL,
    /** The registered fallback, because evaluation could not complete. */
    SYSTEM_FALLBACK;
}
