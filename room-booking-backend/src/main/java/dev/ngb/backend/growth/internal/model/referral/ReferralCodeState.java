package dev.ngb.backend.growth.internal.model.referral;

/**
 * Where a referral code stands.
 */
public enum ReferralCodeState {

    /** Working and earning. */
    ACTIVE,

    /** Stopped pending a decision. */
    SUSPENDED,

    /** Past the date it stopped working. */
    EXPIRED,

    /** Withdrawn for good. */
    REVOKED
}
