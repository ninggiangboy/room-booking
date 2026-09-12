package dev.ngb.backend.model;

/**
 * What one line of a cancellation decision recalculates.
 *
 * <p>Mirrors the booking line types it settles, plus the two that only exist at cancellation time: a
 * fee the policy charges for cancelling, and goodwill the platform or host chooses to give.</p>
 */
public enum DecisionLineCategory {
    /** Nightly accommodation charge. */
    ACCOMMODATION,
    /** Cleaning fee. */
    CLEANING_FEE,
    /** Platform service fee charged to the guest. */
    SERVICE_FEE,
    /** Platform fee charged to the host. */
    HOST_FEE,
    /** Charge for guests beyond the standard occupancy. */
    EXTRA_GUEST_FEE,
    /** Charge for pets. */
    PET_FEE,
    /** Mandatory property fee. */
    RESORT_FEE,
    /** A discount that was applied to the booking. */
    DISCOUNT,
    /** A promotion that was redeemed against the booking. */
    PROMOTION,
    /** Tax. Never itself taxable and never in a commission base. */
    TAX,
    /** A refundable deposit. */
    SECURITY_DEPOSIT,
    /** A fee the policy charges for cancelling. */
    CANCELLATION_FEE,
    /** An amount given beyond what the policy required. */
    GOODWILL,
    /** Anything the catalogue does not yet name. */
    OTHER
}
