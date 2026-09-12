package dev.ngb.backend.model;

/**
 * How far a proposed accounting effect has got.
 *
 * <p>{@code POSTED} is terminal: a posted transaction is never updated or deleted, and a mistake is
 * answered by a reversal plus a new transaction. Because nothing may be added to a posted
 * transaction, the posting service writes the row un-posted, writes its postings, and moves it here
 * within one database transaction.</p>
 */
public enum LedgerTransactionState {
    /** A source fact arrived and is awaiting validation. */
    RECEIVED,
    /** Provenance, accounts, currency, and balance checked. */
    VALIDATED,
    /** Committed to the journal and immutable from this point. */
    POSTED,
    /** Refused with a stable error; the source fact remains recoverable. */
    REJECTED,
    /** Held for a person because an approval workflow applies. */
    REVIEW_REQUIRED
}
