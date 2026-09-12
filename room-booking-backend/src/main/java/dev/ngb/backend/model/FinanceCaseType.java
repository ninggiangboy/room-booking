package dev.ngb.backend.model;

/**
 * What kind of difference a finance reconciliation case is about.
 */
public enum FinanceCaseType {
    /** External evidence the platform cannot account for. */
    MISSING_INTERNAL,
    /** A platform fact no external evidence supports. */
    MISSING_EXTERNAL,
    /** The two sides disagree about how much. */
    AMOUNT_MISMATCH,
    /** The two sides disagree about which currency. */
    CURRENCY_MISMATCH,
    /** The two sides disagree about what happened. */
    STATUS_MISMATCH,
    /** The same movement appears twice on one side. */
    DUPLICATE,
    /** A reference names nothing the platform recognises. */
    UNKNOWN_REFERENCE,
    /** Provider fees differ from what was expected. */
    FEE_VARIANCE,
    /** A difference outstayed its approved timing tolerance. */
    TIMING_BREACH,
    /** A control account balance does not reconcile. */
    BALANCE_BREAK,
    /** Evidence could not be read well enough to compare. */
    PARSER_ERROR
}
