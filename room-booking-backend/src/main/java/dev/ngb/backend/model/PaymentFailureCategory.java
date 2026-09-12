package dev.ngb.backend.model;

/**
 * Normalised reason a payment or refund did not succeed.
 *
 * <p>Provider codes vary; these do not. The category drives what the guest is told and whether a
 * retry is safe, while the provider's own code is kept in a restricted field that never reaches a
 * browser.</p>
 *
 * <p>{@link #OUTCOME_UNKNOWN} is the important one: it means the submission may have taken effect,
 * so the answer is to query or reconcile, never to submit again.</p>
 */
public enum PaymentFailureCategory {
    /** The issuer or provider declined this instrument. */
    PAYMENT_METHOD_DECLINED,
    /** A customer action is required before the payment can proceed. */
    AUTHENTICATION_REQUIRED,
    /** The challenge did not complete or did not verify. */
    AUTHENTICATION_FAILED,
    /** A decline the provider permits disclosing as such. */
    INSUFFICIENT_FUNDS,
    /** Expired, revoked, or unsupported instrument. */
    PAYMENT_METHOD_INVALID,
    /** Platform or provider risk rejected the request. */
    RISK_BLOCKED,
    /** The provider proved no effect and permits a retry. */
    PROVIDER_TEMPORARY_FAILURE,
    /** The submission may have taken effect; query or reconcile, never resubmit. */
    OUTCOME_UNKNOWN,
    /** Merchant, account, or method configuration is invalid. */
    CONFIGURATION_ERROR,
    /** Provider evidence conflicts with the obligation. */
    AMOUNT_OR_CURRENCY_MISMATCH,
    /** No eligible capture remains to return money from. */
    NO_REFUNDABLE_CAPTURE
}
