package dev.ngb.backend.admin.internal.model.role;

/**
 * Whether an operator role is held across the whole platform or only within one market.
 */
public enum OperatorMarketScope {

    /** Held everywhere the platform operates. */
    GLOBAL,

    /** Held in one named market and nowhere else. */
    MARKET
}
