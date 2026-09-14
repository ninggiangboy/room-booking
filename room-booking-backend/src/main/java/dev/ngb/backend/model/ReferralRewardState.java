package dev.ngb.backend.model;

/**
 * Where one side of a referral reward stands.
 *
 * <p>A reward matures before it is granted and is reversed rather than deleted, because the
 * booking that earned it can still be cancelled.</p>
 */
public enum ReferralRewardState {

    /** Owed but still inside the maturity delay. */
    PENDING,

    /** Past the delay and ready to be granted. */
    MATURED,

    /** In the beneficiary’s hands, in a lot or a redemption. */
    GRANTED,

    /** Taken back after being granted. */
    REVERSED,

    /** Never granted, for a recorded reason. */
    FORFEITED
}
