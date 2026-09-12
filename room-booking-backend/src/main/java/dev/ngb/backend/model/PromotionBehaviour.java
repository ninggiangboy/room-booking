package dev.ngb.backend.model;

/**
 * Whether promotions may stack on top of a manually overridden price.
 *
 * <p>A host who has already cut a price by hand often does not intend a campaign to cut it again.
 * Recording the intent beside the override is what prevents that second discount being applied and
 * then argued about after the booking settles.</p>
 */
public enum PromotionBehaviour {
    /** Promotions may still apply to these nights. */
    ALLOW_PROMOTIONS,
    /** The overridden price is final; no promotion may reduce it further. */
    EXCLUDE_PROMOTIONS
}
