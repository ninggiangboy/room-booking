package dev.ngb.backend.support.internal.model.decision;

/**
 * The disclosure constraint of {@code case_decisions}.
 */
public enum DecisionDisclosureConstraint {

    /** None. */
    NONE,

    /** Summary only. */
    SUMMARY_ONLY,

    /** Redact other party. */
    REDACT_OTHER_PARTY,

    /** Authority only. */
    AUTHORITY_ONLY,

    /** Legal review required. */
    LEGAL_REVIEW_REQUIRED
}
