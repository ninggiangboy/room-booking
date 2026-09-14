package dev.ngb.backend.support.internal.model.case_;

/**
 * Where a case is in its lifecycle.
 *
 * <p>State alone does not describe all the work: safety, waiting party, financial execution, appeal and
 * legal hold are tracked separately. {@code CLOSED} is historically terminal, and reopening starts a
 * new lifecycle episode rather than erasing the closure.</p>
 */
public enum SupportCaseState {

    /** New. */
    NEW,

    /** Triaged. */
    TRIAGED,

    /** Assigned. */
    ASSIGNED,

    /** Investigating. */
    INVESTIGATING,

    /** Decision pending. */
    DECISION_PENDING,

    /** Remedy pending. */
    REMEDY_PENDING,

    /** Escalated. */
    ESCALATED,

    /** Resolved. */
    RESOLVED,

    /** Closed. */
    CLOSED,

    /** Reopened. */
    REOPENED,

    /** Appeal pending. */
    APPEAL_PENDING,

    /** Duplicate. */
    DUPLICATE,

    /** Withdrawn. */
    WITHDRAWN
}
