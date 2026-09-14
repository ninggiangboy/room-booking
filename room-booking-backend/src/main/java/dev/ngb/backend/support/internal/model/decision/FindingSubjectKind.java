package dev.ngb.backend.support.internal.model.decision;

/**
 * The subject kind of {@code case_findings}.
 */
public enum FindingSubjectKind {

    /** Case. */
    CASE,

    /** Party. */
    PARTY,

    /** Booking. */
    BOOKING,

    /** Listing. */
    LISTING,

    /** Claim item. */
    CLAIM_ITEM,

    /** Payment. */
    PAYMENT,

    /** Provider dispute. */
    PROVIDER_DISPUTE,

    /** Coverage. */
    COVERAGE
}
