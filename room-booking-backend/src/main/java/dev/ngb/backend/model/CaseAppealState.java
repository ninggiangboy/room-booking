package dev.ngb.backend.model;

/**
 * The state of {@code case_appeals}.
 */
public enum CaseAppealState {

    /** Submitted. */
    SUBMITTED,

    /** Eligibility review. */
    ELIGIBILITY_REVIEW,

    /** Ineligible. */
    INELIGIBLE,

    /** Assigned. */
    ASSIGNED,

    /** Reviewing. */
    REVIEWING,

    /** Decided. */
    DECIDED,

    /** Communicated. */
    COMMUNICATED,

    /** Closed. */
    CLOSED,

    /** Withdrawn. */
    WITHDRAWN
}
