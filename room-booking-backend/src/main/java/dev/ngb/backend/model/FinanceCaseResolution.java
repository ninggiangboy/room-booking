package dev.ngb.backend.model;

/**
 * How a finance reconciliation case was answered.
 *
 * <p>Resolution links evidence and any approved correction. It never overwrites either side of the
 * comparison, and the matching engine never inserts a balancing entry purely to make a report
 * agree.</p>
 */
public enum FinanceCaseResolution {
    /** The difference was a known timing effect and has since cleared. */
    CONFIRMED_TIMING,
    /** The external side is wrong and its owner was asked to fix it. */
    PROVIDER_CORRECTION_REQUESTED,
    /** A platform fact was missing and has been recovered properly. */
    INTERNAL_FACT_RECOVERED,
    /** A duplicate was identified and compensated. */
    DUPLICATE_COMPENSATED,
    /** An approved correcting transaction was posted. */
    JOURNAL_CORRECTION,
    /** Answered by returning or sending money under an approved command. */
    REFUND_OR_PAYOUT_ACTION,
    /** Approved as unrecoverable, without deleting its history. */
    WRITTEN_OFF,
    /** The control was wrong; nothing was actually out. */
    FALSE_POSITIVE
}
