package dev.ngb.backend.model;

/**
 * The observed state of {@code external_claim_observations}.
 */
public enum ExternalClaimObservedState {

    /** Submitted. */
    SUBMITTED,

    /** Provider review. */
    PROVIDER_REVIEW,

    /** Info required. */
    INFO_REQUIRED,

    /** Approved. */
    APPROVED,

    /** Partial. */
    PARTIAL,

    /** Denied. */
    DENIED,

    /** Payment pending. */
    PAYMENT_PENDING,

    /** Paid. */
    PAID,

    /** Withdrawn. */
    WITHDRAWN,

    /** Unknown. */
    UNKNOWN
}
