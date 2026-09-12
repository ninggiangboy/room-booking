package dev.ngb.backend.model;

/**
 * What ultimately decided a materialized nightly price.
 *
 * <p>The one field that answers "why is this night this price" before anyone reads the breakdown.
 * A row claiming a manual origin must name the override that produced it, so the attribution cannot
 * drift from the evidence.</p>
 */
public enum PriceSourceDecision {
    /** The host's base amount, with nothing applied. */
    BASE,
    /** Published rule versions applied to the base amount. */
    RULE_ENGINE,
    /** A model recommendation the host accepted or automation applied. */
    RECOMMENDATION,
    /** A host's explicit instruction for these nights. */
    MANUAL_OVERRIDE
}
