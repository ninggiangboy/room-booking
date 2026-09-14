package dev.ngb.backend.analytics.internal.model.contract;

/**
 * What kind of claim a correction makes.
 *
 * <p>Suppression removes and never substitutes; a semantic correction must supply the replacement
 * it asserts.</p>
 */
public enum DataCorrectionKind {

    /** The value was wrong and a replacement is supplied. */
    SEMANTIC_CORRECTION,

    /** The record should not be relied on; needs independent approval. */
    INVALIDATION,

    /** Removed under a subject request; never substitutes a replacement. */
    PRIVACY_SUPPRESSION,

    /** A late but valid fact reopens a period. */
    LATE_RESTATEMENT
}
