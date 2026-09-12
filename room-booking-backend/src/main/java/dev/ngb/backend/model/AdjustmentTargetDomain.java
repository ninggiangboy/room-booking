package dev.ngb.backend.model;

/**
 * Which domain is expected to act on an adjustment instruction.
 *
 * <p>Stored rather than derived from the type, because the routing is a deployment fact: the same kind
 * of adjustment may be handled by different owners as the platform grows.</p>
 */
public enum AdjustmentTargetDomain {
    /** The ledger, recovery and payout domain. */
    FINANCE,
    /** The payment orchestration domain. */
    PAYMENTS,
    /** Pricing and promotions. */
    PRICING,
    /** Tax calculation and remittance. */
    TAX,
    /** Invoice and credit-note issuance. */
    DOCUMENTS,
    /** Credits, referrals and stored value. */
    LOYALTY
}
