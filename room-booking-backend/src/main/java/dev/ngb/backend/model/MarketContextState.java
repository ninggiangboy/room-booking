package dev.ngb.backend.model;

/**
 * Whether a row's market context is known or merely inherited from before markets existed.
 *
 * <p>Rows created before migration {@code 013} have no market, and the multi-market design forbids
 * inferring one from a currency, phone number, or address. Marking them
 * {@code LEGACY_UNRECONCILED} is the honest alternative: it blocks consequential workflows until an
 * operator resolves the market, rather than letting a contract form under rules nobody approved.</p>
 */
public enum MarketContextState {
    /** The market is known and recorded. */
    RESOLVED,
    /** Predates market configuration; must be reconciled before consequential use. */
    LEGACY_UNRECONCILED
}
