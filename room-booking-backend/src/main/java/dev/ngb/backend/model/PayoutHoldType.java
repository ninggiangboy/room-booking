package dev.ngb.backend.model;

/**
 * Why money is being stopped.
 *
 * <p>A hold changes availability for payout. It never reclassifies host-owned money as platform
 * revenue, which is why the type is a reason rather than an accounting instruction.</p>
 */
public enum PayoutHoldType {
    /** An incident or damage claim on the stay is open. */
    BOOKING_INCIDENT,
    /** The collection backing this amount is not yet proven. */
    PAYMENT_OUTCOME,
    /** A dispute could reclaim the guest's money. */
    CHARGEBACK,
    /** Host identity verification is incomplete. */
    IDENTITY,
    /** A required tax identifier or registration is missing. */
    TAX_READINESS,
    /** A sanctions or watchlist check is unresolved. */
    SANCTIONS,
    /** The payout destination is too newly added to send to. */
    DESTINATION_COOLING_OFF,
    /** The account shows signs of compromise. */
    ACCOUNT_TAKEOVER_RISK,
    /** An external authority has required the funds be held. */
    LEGAL_ORDER,
    /** The host owes more than this amount and it is funding the recovery. */
    NEGATIVE_BALANCE_RECOVERY,
    /** An unexplained difference touches this money. */
    RECONCILIATION_EXCEPTION,
    /** An operator stopped it under an emergency procedure. */
    MANUAL_EMERGENCY
}
