package dev.ngb.backend.model;

/**
 * What a host did about a piece of advice. Refusing and ignoring are recorded as deliberately as
 * accepting: a system that only stores acceptances cannot tell a good recommendation from one
 * nobody dared refuse.
 */
public enum AdviceDecisionOutcome {

    /** The host took the advice as offered. */
    ACCEPTED,

    /** The host declined it and said why. */
    REJECTED,

    /** The host took the advice but not as offered, and what they changed is recorded. */
    MODIFIED,

    /** The advice lapsed without an answer; the platform observed this rather than the host deciding it. */
    IGNORED,

    /** The advice went stale before the host reached it. */
    EXPIRED
}
