package dev.ngb.backend.discovery.internal.model.event;

import dev.ngb.backend.supply.internal.model.listing.Listing;

import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * Why a search was served by something other than its intended ranker.
 *
 * <p>Degrading is acceptable; degrading silently is not.</p>
 */
public enum DiscoveryFallbackCode {

    /** None. */
    NONE,

    /** Model timeout. */
    MODEL_TIMEOUT,

    /** Model error. */
    MODEL_ERROR,

    /** Profile unavailable. */
    PROFILE_UNAVAILABLE,

    /** Listing profile stale. */
    LISTING_PROFILE_STALE,

    /** Experiment config invalid. */
    EXPERIMENT_CONFIG_INVALID,

    /** Pricing unavailable. */
    PRICING_UNAVAILABLE,

    /** Feature lookup failed. */
    FEATURE_LOOKUP_FAILED
}
