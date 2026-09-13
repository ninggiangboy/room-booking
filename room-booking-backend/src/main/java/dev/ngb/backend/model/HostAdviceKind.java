package dev.ngb.backend.model;

/**
 * The kinds of advice the platform gives a host about their own business.
 */
public enum HostAdviceKind {

    /** Price recommendation. */
    PRICE_RECOMMENDATION,

    /** Demand forecast. */
    DEMAND_FORECAST,

    /** Promotion suggestion. */
    PROMOTION_SUGGESTION,

    /** Quality checklist item. */
    QUALITY_CHECKLIST_ITEM
}
