package dev.ngb.backend.model;

/**
 * How much freedom the pricing engine has over a host's calendar.
 *
 * <p>This is the host's consent, recorded explicitly. The difference between proposing a price and
 * setting one is the difference between advice and spending someone else's money, so it is never
 * inferred from the strategy.</p>
 */
public enum PricingAutomationState {
    /** The engine may not write prices at all. */
    OFF,
    /** The engine may propose prices; a human accepts or rejects each one. */
    SUGGEST,
    /** The engine may set prices directly, within the configured bounds. */
    APPLY
}
