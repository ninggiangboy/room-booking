package dev.ngb.backend.support.internal.model.evidence;

/**
 * Where an evidence artifact stands in its custody lifecycle.
 *
 * <p>Nothing becomes usable before it is known to be safe, and deletion preserves a tombstone.</p>
 */
public enum CaseEvidenceState {

    /** Registered. */
    REGISTERED,

    /** Quarantined. */
    QUARANTINED,

    /** Available. */
    AVAILABLE,

    /** Included in review. */
    INCLUDED_IN_REVIEW,

    /** Included in submission. */
    INCLUDED_IN_SUBMISSION,

    /** Rejected unsafe. */
    REJECTED_UNSAFE,

    /** Redacted derivative created. */
    REDACTED_DERIVATIVE_CREATED,

    /** Retention expired. */
    RETENTION_EXPIRED,

    /** Deletion pending. */
    DELETION_PENDING,

    /** Deleted or crypto erased. */
    DELETED_OR_CRYPTO_ERASED
}
