package dev.ngb.backend.model;

/**
 * Why a revision was written.
 *
 * <p>After reveal only the correction kinds are permitted, which is why they are named kinds rather
 * than exceptions a service may grant itself.</p>
 */
public enum ReviewRevisionKind {
    /** The author saying what they think. */
    AUTHOR_SUBMISSION,
    /** A spelling or formatting fix that does not change meaning. */
    TYPOGRAPHICAL_CORRECTION,
    /** Removal of personal data that should not have been there. */
    PRIVACY_REDACTION,
    /** A change required by law or a legal order. */
    LEGAL_CORRECTION,
    /** History brought forward, marked as such rather than dressed up as a submission. */
    LEGACY_IMPORT
}
