package dev.ngb.backend.support.internal.model.case_;

/**
 * The pause reason of {@code case_sla_clocks}.
 */
public enum SlaPauseReason {

    /** Awaiting claimant evidence. */
    AWAITING_CLAIMANT_EVIDENCE,

    /** Awaiting respondent statement. */
    AWAITING_RESPONDENT_STATEMENT,

    /** Awaiting customer reply. */
    AWAITING_CUSTOMER_REPLY,

    /** Awaiting third party. */
    AWAITING_THIRD_PARTY,

    /** Outside business hours. */
    OUTSIDE_BUSINESS_HOURS,

    /** Approved exception. */
    APPROVED_EXCEPTION
}
