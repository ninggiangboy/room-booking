package dev.ngb.backend.model;

/**
 * The outcome of {@code case_decisions}.
 */
public enum CaseDecisionOutcome {

    /** Granted. */
    GRANTED,

    /** Partially granted. */
    PARTIALLY_GRANTED,

    /** Refused. */
    REFUSED,

    /** Referred. */
    REFERRED,

    /** No action. */
    NO_ACTION,

    /** Affirmed. */
    AFFIRMED,

    /** Modified. */
    MODIFIED,

    /** Reversed. */
    REVERSED,

    /** Remanded. */
    REMANDED
}
