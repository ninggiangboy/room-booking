package dev.ngb.backend.stay.internal.model.incident;

/**
 * What an incident is asking another domain for.
 *
 * <p>Operations renders the answer. It never writes the other domain's tables.</p>
 */
public enum RemedyActionType {
    /** Escalate contact with a party. */
    CONTACT_ESCALATION,
    /** Authorize a manual way in. */
    MANUAL_ACCESS_FALLBACK,
    /** Block future dates immediately. */
    INVENTORY_EMERGENCY_BLOCK,
    /** Price a cancellation without performing it. */
    CANCELLATION_PREVIEW,
    /** Perform a cancellation. */
    CANCELLATION_EXECUTE,
    /** Price a refund without performing it. */
    REFUND_PREVIEW,
    /** Perform a refund. */
    REFUND_EXECUTE,
    /** Issue credit to the guest. */
    GUEST_CREDIT,
    /** Waive a fee. */
    FEE_WAIVER,
    /** Search for replacement supply. */
    RELOCATION_SEARCH,
    /** Authorize a relocation budget. */
    RELOCATION_BUDGET,
    /** Stop collecting money for now. */
    COLLECTION_PAUSE,
    /** Hold the host's funds. */
    PAYOUT_HOLD,
    /** Open a risk review. */
    RISK_REVIEW,
    /** Restrict a party on safety grounds. */
    SAFETY_RESTRICTION,
    /** Open a damage claim. */
    CLAIMS_INTAKE
}
