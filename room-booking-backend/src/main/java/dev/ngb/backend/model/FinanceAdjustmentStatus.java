package dev.ngb.backend.model;

/**
 * How far a manual adjustment has travelled through maker-checker.
 *
 * <p>Approval is bound to an exact request hash. Any material edit invalidates the approval rather
 * than carrying it forward, and a request cannot reach {@link #APPROVED} with fewer approvals than
 * its own type demands.</p>
 */
public enum FinanceAdjustmentStatus {
    /** Being written. */
    DRAFT,
    /** Awaiting approval. */
    SUBMITTED,
    /** Approved to the threshold its type requires. */
    APPROVED,
    /** Refused, with a reason. */
    REJECTED,
    /** Committed to the journal; the transaction is named on the row. */
    POSTED,
    /** Approved but failed to post; a person must look. */
    FAILED_REVIEW_REQUIRED
}
