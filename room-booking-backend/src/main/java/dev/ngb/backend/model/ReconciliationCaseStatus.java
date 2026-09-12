package dev.ngb.backend.model;

/**
 * Progress of a reconciliation case.
 */
public enum ReconciliationCaseStatus {
    /** Raised, unassigned. */
    OPEN,
    /** Someone is working it. */
    INVESTIGATING,
    /** Waiting on the provider or another domain. */
    BLOCKED,
    /** Closed with a recorded resolution. */
    RESOLVED,
    /** Closed by accepting the loss, with approval. */
    WRITTEN_OFF
}
