package dev.ngb.backend.model;

/**
 * Why a promotion exists.
 *
 * <p>Recorded because it decides how the promotion should be judged. Acquisition spend that produces
 * no new guests has failed even if it filled nights; occupancy spend that filled nights has
 * succeeded even if every guest was a returning one.</p>
 */
public enum PromotionPurpose {
    /** Bring in guests who have not booked before. */
    ACQUISITION,
    /** Bring back guests who have. */
    RETENTION,
    /** Sell nights that would otherwise go empty. */
    OCCUPANCY_FILL,
    /** Support a new market, listing, or product at launch. */
    LAUNCH,
    /** Respond to a known seasonal pattern. */
    SEASONAL,
    /** Make good after something went wrong for a guest. */
    SERVICE_RECOVERY,
    /** Honour an agreement with an external partner. */
    PARTNERSHIP
}
