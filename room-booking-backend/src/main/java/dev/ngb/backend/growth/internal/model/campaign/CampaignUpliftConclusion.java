package dev.ngb.backend.growth.internal.model.campaign;

/**
 * What a measured campaign effect amounts to.
 *
 * <p>The conclusion follows from where the interval sits relative to zero rather than from
 * anybody’s reading of it, and the middle value is the honest answer that the campaign was not
 * measurably either thing.</p>
 */
public enum CampaignUpliftConclusion {

    /** The interval around the effect lies wholly above zero. */
    INCREMENTAL,

    /** The interval contains zero, so nothing was measured either way. */
    NO_DETECTED_EFFECT,

    /** The interval lies wholly below zero. */
    HARMFUL
}
