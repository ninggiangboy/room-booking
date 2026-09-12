package dev.ngb.backend.model;

/**
 * Progress of one reconciliation run.
 */
public enum ReconciliationRunState {
    /** In progress. */
    RUNNING,
    /** Finished; its entries are the record of what was compared. */
    COMPLETED,
    /** Stopped without completing. */
    FAILED,
    /** Stopped by an operator. */
    CANCELLED
}
