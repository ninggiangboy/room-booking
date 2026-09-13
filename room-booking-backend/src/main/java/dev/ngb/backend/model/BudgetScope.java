package dev.ngb.backend.model;

/**
 * The budget scope of {@code remedy_budget_windows}.
 */
public enum BudgetScope {

    /** Per agent day. */
    PER_AGENT_DAY,

    /** Per team day. */
    PER_TEAM_DAY,

    /** Per case. */
    PER_CASE,

    /** Per user period. */
    PER_USER_PERIOD,

    /** Per campaign. */
    PER_CAMPAIGN,

    /** Per market period. */
    PER_MARKET_PERIOD,

    /** Global period. */
    GLOBAL_PERIOD
}
