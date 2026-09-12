package dev.ngb.backend.model;

/**
 * How a reconciliation case was closed.
 *
 * <p>Editing a provider reference until a report balances is not on this list, and is never an
 * acceptable resolution.</p>
 */
public enum ReconciliationResolution {
    /** Provider evidence proved the mapping. */
    MATCHED_WITH_EVIDENCE,
    /** An audited domain command attached or corrected the record. */
    REPAIRED_BY_COMMAND,
    /** The money was returned to the payer. */
    REFUNDED,
    /** An unused reservation was released. */
    VOIDED,
    /** The provider amended its own record. */
    PROVIDER_CORRECTED,
    /** Investigation showed both sides were already correct. */
    NO_ACTION_REQUIRED,
    /** Accepted as a loss under approval. */
    WRITTEN_OFF
}
