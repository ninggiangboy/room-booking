package dev.ngb.backend.model;

/**
 * Where a loyalty membership stands.
 */
public enum LoyaltyMembershipState {

    /** Accruing and holding a tier. */
    ACTIVE,

    /** Not accruing, but still on the books. */
    LAPSED,

    /** Held pending a decision, for a recorded reason. */
    SUSPENDED,

    /** Finished, for a recorded reason. */
    CLOSED
}
