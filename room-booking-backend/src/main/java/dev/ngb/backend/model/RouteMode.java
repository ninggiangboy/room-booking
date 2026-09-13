package dev.ngb.backend.model;

/**
 * How much real traffic a release route carries.
 */
public enum RouteMode {

    /** Carries no traffic. */
    DISABLED,

    /** Predictions are produced and logged but nothing acts on them. */
    SHADOW,

    /** A bounded share of real traffic, under guardrails and a rollback target. */
    CANARY,

    /** The live path for this consumer, scope and market. */
    ACTIVE
}
