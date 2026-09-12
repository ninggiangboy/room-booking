package dev.ngb.backend.model;

/**
 * Which domain placed a hold.
 *
 * <p>Stored so a hold can be routed back to the team that owns its reason. Finance cannot release a
 * compliance hold by deciding it looks stale.</p>
 */
public enum PayoutHoldIssuer {
    /** Placed by finance operations, typically a reconciliation exception. */
    FINANCE,
    /** Placed by fraud or account-takeover detection. */
    RISK,
    /** Placed for identity, tax, or sanctions reasons. */
    COMPLIANCE,
    /** Placed while a case involving the booking is open. */
    SUPPORT,
    /** Placed under a legal or regulatory order. */
    LEGAL,
    /** Placed because the collection outcome is unresolved or disputed. */
    PAYMENTS,
    /** Placed by an operator under an emergency procedure. */
    MANUAL
}
