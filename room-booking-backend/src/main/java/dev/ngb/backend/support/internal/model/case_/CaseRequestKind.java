package dev.ngb.backend.support.internal.model.case_;

/**
 * The request kind of {@code support_cases}.
 */
public enum CaseRequestKind {

    /** Information. */
    INFORMATION,

    /** Operational help. */
    OPERATIONAL_HELP,

    /** Contract change. */
    CONTRACT_CHANGE,

    /** Refund or credit. */
    REFUND_OR_CREDIT,

    /** Compensation. */
    COMPENSATION,

    /** Claim. */
    CLAIM,

    /** Provider dispute. */
    PROVIDER_DISPUTE,

    /** Appeal. */
    APPEAL,

    /** Complaint. */
    COMPLAINT
}
