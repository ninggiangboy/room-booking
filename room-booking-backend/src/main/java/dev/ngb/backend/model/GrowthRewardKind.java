package dev.ngb.backend.model;

/**
 * What form the reward under a set of programme terms takes.
 *
 * <p>{@code PROMOTION} reaches the guest through migration 019’s promotion machinery rather
 * than a second discount path, which is what keeps stacking, tax treatment and the host-funded
 * share under the rules that govern every other discount on a quote.</p>
 */
public enum GrowthRewardKind {

    /** Stored value granted as a lot the guest can spend. */
    CREDIT,

    /** A discount reaching the guest through migration 019. */
    PROMOTION,

    /** A loyalty benefit rather than an amount. */
    TIER_BENEFIT,

    /** A share of a booking paid to a partner. */
    COMMISSION,

    /** Face value on a card somebody bought. */
    GIFT_CARD_VALUE,

    /** The programme grants nothing; it only contacts people. */
    NONE
}
