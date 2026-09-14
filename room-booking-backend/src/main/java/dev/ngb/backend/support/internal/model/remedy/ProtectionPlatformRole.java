package dev.ngb.backend.support.internal.model.remedy;

/**
 * The role the platform actually plays in a protection product.
 *
 * <p>Marketing, terms, eligibility, claims authority and licensing must match this; until a market
 * approves the role, neutral internal terms are used and no insurance cover is promised.</p>
 */
public enum ProtectionPlatformRole {

    /** A contractual protection programme the platform itself offers. */
    CONTRACTUAL_PROTECTION,

    /** The platform distributes or intermediates a carrier product. */
    INSURANCE_DISTRIBUTOR,

    /** The platform only links to an independent policy. */
    INTRODUCER_ONLY
}
