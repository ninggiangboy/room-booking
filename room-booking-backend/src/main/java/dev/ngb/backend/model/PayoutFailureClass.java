package dev.ngb.backend.model;

/**
 * What kind of failure a payout hit, and therefore what may be done about it.
 *
 * <p>A failure without a classification cannot be routed, which is why the database refuses one.
 * {@link #PROVIDER_UNKNOWN} is the one that matters most: it is not a failure at all but an absence
 * of evidence, and it is resolved by querying the provider, never by sending again.</p>
 */
public enum PayoutFailureClass {
    /** Failed before reaching the provider; safe to retry as-is. */
    PRE_SUBMISSION_RETRYABLE,
    /** No outcome was observed; query by provider reference before doing anything. */
    PROVIDER_UNKNOWN,
    /** The rail refused in a way worth retrying after a policy delay. */
    RAIL_RETRYABLE,
    /** The host must correct something before it can succeed. */
    HOST_ACTION_REQUIRED,
    /** Stopped for a compliance reason rather than a technical one. */
    COMPLIANCE_HOLD,
    /** The destination cannot receive this transfer at all. */
    DESTINATION_TERMINAL,
    /** Needs a person to establish what actually happened. */
    MANUAL_RECONCILIATION
}
