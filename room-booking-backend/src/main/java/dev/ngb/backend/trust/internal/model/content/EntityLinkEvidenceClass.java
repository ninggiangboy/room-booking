package dev.ngb.backend.trust.internal.model.content;

/**
 * How the relationship was established.
 */
public enum EntityLinkEvidenceClass {
    /** Checked by this platform. */
    VERIFIED,
    /** From an authenticated provider observation. */
    AUTHENTICATED,
    /** Supported by independent observations. */
    CORROBORATED,
    /** Deterministically derived. */
    DERIVED,
    /** Inferred, and on its own not enough for an adverse decision. */
    INFERRED
}
