package dev.ngb.backend.review.internal.model.publication;

/**
 * What a public rating aggregate counts for.
 */
public enum AggregateSubjectType {
    /** One listing. */
    LISTING,
    /** One host across their listings. */
    HOST,
    /** One guest, from host-to-guest feedback. */
    GUEST,
    /** One property. */
    PROPERTY
}
