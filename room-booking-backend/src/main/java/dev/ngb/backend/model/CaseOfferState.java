package dev.ngb.backend.model;

/**
 * Where a structured offer stands.
 *
 * <p>Acceptance is an explicit authenticated command against an exact version and digest; silence and
 * free-text agreement are not acceptance.</p>
 */
public enum CaseOfferState {

    /** Draft. */
    DRAFT,

    /** Sent. */
    SENT,

    /** Viewed. */
    VIEWED,

    /** Accepted. */
    ACCEPTED,

    /** Rejected. */
    REJECTED,

    /** Countered. */
    COUNTERED,

    /** Expired. */
    EXPIRED,

    /** Withdrawn. */
    WITHDRAWN
}
