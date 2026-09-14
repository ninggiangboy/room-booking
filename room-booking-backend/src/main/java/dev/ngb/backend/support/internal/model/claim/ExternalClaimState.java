package dev.ngb.backend.support.internal.model.claim;

/**
 * Where a provider-held claim stands, from local approval to reconciliation.
 *
 * <p>Provider acceptance is evidence of a decision; only reconciliation against ledger evidence shows
 * the money arrived.</p>
 */
public enum ExternalClaimState {

    /** Local approved. */
    LOCAL_APPROVED,

    /** Submission queued. */
    SUBMISSION_QUEUED,

    /** Submitting. */
    SUBMITTING,

    /** Submitted. */
    SUBMITTED,

    /** Unknown. */
    UNKNOWN,

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

    /** Reconciled. */
    RECONCILED,

    /** Appeal or complaint. */
    APPEAL_OR_COMPLAINT,

    /** Withdrawn. */
    WITHDRAWN
}
