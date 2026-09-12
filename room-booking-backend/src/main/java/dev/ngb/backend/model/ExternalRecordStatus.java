package dev.ngb.backend.model;

/**
 * What the external source says about a row's own state.
 *
 * <p>Normalized from the provider's native status, which is kept alongside. The distinction between
 * pending and available is a common source of timing differences and is preserved rather than
 * flattened.</p>
 */
public enum ExternalRecordStatus {
    /** Recorded by the provider but not yet available. */
    PENDING,
    /** Available in the provider balance. */
    AVAILABLE,
    /** Paid out or finally cleared. */
    SETTLED,
    /** Did not complete. */
    FAILED,
    /** Completed and came back. */
    RETURNED,
    /** The source does not say. */
    UNKNOWN
}
