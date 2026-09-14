package dev.ngb.backend.model;

/**
 * Where a gift card stands between issue and redemption.
 */
public enum GiftCardState {

    /** Created but not yet usable. */
    ISSUED,

    /** Usable. */
    ACTIVE,

    /** Its face value has moved into a balance. */
    REDEEMED,

    /** Past its expiry. */
    EXPIRED,

    /** Cancelled, for a recorded reason. */
    VOIDED
}
