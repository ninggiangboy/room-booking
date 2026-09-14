package dev.ngb.backend.model;

/**
 * Whether an eligibility decision was taken about a signed-in person or an
 * anonymous visitor.
 */
public enum GrowthSubjectKind {

    /** A signed-in person. */
    ACCOUNT_HOLDER,

    /** A visitor known only by a pseudonymous key. */
    ANONYMOUS_UNIT
}
