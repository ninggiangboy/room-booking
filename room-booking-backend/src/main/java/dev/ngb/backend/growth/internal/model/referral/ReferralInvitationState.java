package dev.ngb.backend.growth.internal.model.referral;

/**
 * Where a referral invitation stands.
 */
public enum ReferralInvitationState {

    /** Sent and not yet acted on. */
    PENDING,

    /** Opened but not accepted. */
    VIEWED,

    /** Somebody signed up through it. */
    ACCEPTED,

    /** Past the date it could be accepted. */
    EXPIRED,

    /** Never sent, for a recorded reason. */
    SUPPRESSED
}
