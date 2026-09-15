package dev.ngb.backend.identity.internal.model.account;

/**
 * Whether a row's market context is known or has never been resolved.
 *
 * <p>No market-resolution signal is available at registration time, and the multi-market design
 * forbids inferring one from a currency, phone number, or address. Marking a holder
 * {@code UNRESOLVED} is the honest alternative: it blocks consequential workflows until an
 * operator resolves the market, rather than letting a contract form under rules nobody approved.
 * Migration {@code 037} renamed this state from {@code LEGACY_UNRECONCILED}: once the legacy
 * schema it referred to was retired, "legacy" no longer named anything real.</p>
 */
public enum MarketContextState {
    /** The market is known and recorded. */
    RESOLVED,
    /** No market has been recorded yet; must be resolved before consequential use. */
    UNRESOLVED
}
