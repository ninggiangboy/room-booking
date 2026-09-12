package dev.ngb.backend.model;

/**
 * What kind of problem a reconciliation case is about.
 */
public enum ReconciliationCaseCategory {
    /** Provider money with no proven internal owner. */
    ORPHAN_TRANSACTION,
    /** The two sides disagree about how much moved. */
    AMOUNT_MISMATCH,
    /** The two sides disagree about which currency moved. */
    CURRENCY_MISMATCH,
    /** Money moved under an unexpected merchant account. */
    ACCOUNT_MISMATCH,
    /** The two sides disagree about the outcome. */
    STATE_MISMATCH,
    /** One intent appears to have produced two provider objects. */
    DUPLICATE_SUSPECTED,
    /** An internal operation the provider has no record of. */
    MISSING_PROVIDER,
    /** A refund and a dispute may both have returned the same money. */
    DOUBLE_CREDIT_EXPOSURE
}
