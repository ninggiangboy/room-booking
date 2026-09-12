package dev.ngb.backend.model;

/**
 * Why a host owes the platform money.
 *
 * <p>The causal instruction states which party funds the effect and the maximum recoverable amount.
 * A recovery never deletes the payout that preceded it; it sits beside it.</p>
 */
public enum HostRecoveryCause {
    /** A cancellation reduced earnings after they were paid. */
    CANCELLATION,
    /** A refund returned money the host had already received. */
    REFUND,
    /** A cardholder's bank reclaimed the guest's payment. */
    CHARGEBACK,
    /** A dispute resolved against the host. */
    DISPUTE,
    /** An approved remedy to a guest that the host funds. */
    GUEST_REMEDY,
    /** Tax withholding was understated and must be recovered. */
    WITHHOLDING_CORRECTION,
    /** The host was paid twice for the same entitlement. */
    DUPLICATE_PAYOUT,
    /** A fee charged because a transfer was returned. */
    PAYOUT_RETURN_FEE,
    /** An approved manual adjustment created the debt. */
    APPROVED_ADJUSTMENT
}
