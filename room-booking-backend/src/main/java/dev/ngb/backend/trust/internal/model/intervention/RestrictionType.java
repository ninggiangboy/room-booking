package dev.ngb.backend.trust.internal.model.intervention;

/**
 * What a restriction stops.
 *
 * <p>Checked at the authoritative command boundary rather than hidden in a screen. Account
 * suspension is a governed command of its own, not an implicit consequence of every refusal.</p>
 */
public enum RestrictionType {
    /** Signing in is refused. */
    LOGIN_BLOCKED,
    /** Existing sessions are ended. */
    SESSION_REVOKED,
    /** Messaging is rate limited or narrowed. */
    MESSAGING_LIMITED,
    /** Publishing a listing is refused. */
    LISTING_PUBLICATION_BLOCKED,
    /** An existing listing is withdrawn from view. */
    LISTING_QUARANTINED,
    /** Creating a booking is refused. */
    BOOKING_CREATE_BLOCKED,
    /** Payment requires a step-up first. */
    PAYMENT_CHALLENGE_REQUIRED,
    /** One instrument may not be used. */
    PAYMENT_METHOD_BLOCKED,
    /** Funds are not released. */
    PAYOUT_RELEASE_HELD,
    /** The payout destination may not be changed. */
    PAYOUT_DESTINATION_LOCKED,
    /** Promotions may not be redeemed. */
    PROMOTION_REDEMPTION_BLOCKED,
    /** Reviews may not be submitted. */
    REVIEW_SUBMISSION_BLOCKED,
    /** Content is withheld pending a decision. */
    CONTENT_QUARANTINED,
    /** The account is suspended. */
    ACCOUNT_SUSPENDED;
}
