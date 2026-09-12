package dev.ngb.backend.model;

/**
 * How a host's nightly price is arrived at.
 *
 * <p>This says where the number comes from, not how freely the platform may write it — that is
 * {@link PricingAutomationState}. A host can follow a recommendation strategy while still insisting
 * on approving every change.</p>
 */
public enum PricingStrategy {
    /** The host sets prices themselves; the engine only stores what they chose. */
    MANUAL,
    /** Prices derive from published rule versions applied to a base amount. */
    RULE_BASED,
    /** Prices follow model recommendations within the host's bounds. */
    RECOMMENDED,
    /** Prices are set by the optimizer without per-night host involvement. */
    AUTOMATED
}
