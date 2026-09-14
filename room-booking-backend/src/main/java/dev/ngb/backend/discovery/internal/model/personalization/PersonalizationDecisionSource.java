package dev.ngb.backend.discovery.internal.model.personalization;

import dev.ngb.backend.market.internal.model.market.Market;

import dev.ngb.backend.market.internal.model.market.Market;

/**
 * Whose decision the personalization settings record.
 *
 * <p>A choice the guest did not make must say whose it was.</p>
 */
public enum PersonalizationDecisionSource {

    /** Default. */
    DEFAULT,

    /** Guest choice. */
    GUEST_CHOICE,

    /** Onboarding. */
    ONBOARDING,

    /** Market policy. */
    MARKET_POLICY,

    /** Regulatory requirement. */
    REGULATORY_REQUIREMENT,

    /** Support action. */
    SUPPORT_ACTION
}
