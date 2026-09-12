package dev.ngb.backend.model;

/**
 * Verdict on comparing one external row against internal records.
 *
 * <p>An orphan is never attached to a booking by resemblance. Exact operation metadata, provider
 * request key, provider reference, and merchant account are the only mappings that count.</p>
 */
public enum ReconciliationOutcome {
    /** One internal operation accounts for the row. */
    MATCHED,
    /** Both sides agree; they were observed at different times. */
    TIMING_DIFFERENCE,
    /** An internal operation the provider does not report. */
    MISSING_PROVIDER,
    /** A provider object no internal operation explains. */
    MISSING_INTERNAL_OR_ORPHAN,
    /** Matched identity, disagreeing amount. */
    AMOUNT_MISMATCH,
    /** Matched identity, disagreeing currency. */
    CURRENCY_MISMATCH,
    /** Matched identity under the wrong merchant account. */
    ACCOUNT_MISMATCH,
    /** Matched identity, disagreeing outcome. */
    STATE_MISMATCH,
    /** More than one provider object appears to answer one intent. */
    DUPLICATE_SUSPECTED
}
