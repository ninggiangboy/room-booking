package dev.ngb.backend.model;

/**
 * Which population a price rule governs.
 *
 * <p>The scope decides both who is affected and which subject column the rule must name. A rule that
 * claims market scope without a market would apply everywhere, which is why
 * {@code ck_price_rules_scope_subject} rejects it rather than letting the mistake ship.</p>
 */
public enum PriceRuleScope {
    /** Applies across the whole platform. */
    PLATFORM,
    /** Applies within one market. */
    MARKET,
    /** Applies to everything one host owns. */
    HOST,
    /** Applies to one accommodation type. */
    ACCOMMODATION_TYPE,
    /** Applies to one rate plan of one accommodation type. */
    RATE_PLAN
}
