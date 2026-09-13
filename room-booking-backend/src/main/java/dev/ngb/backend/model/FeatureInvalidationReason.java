package dev.ngb.backend.model;

/**
 * Why stored values of a feature stopped being usable.
 *
 * <p>Every one of these originates outside the model owner's control: an upstream correction, a
 * person exercising a right, a source that turned out to be wrong, a definition retired.</p>
 */
public enum FeatureInvalidationReason {

    /** An upstream correction changed what the source said. */
    CORRECTION,

    /** A person exercised their right to erasure. */
    SUBJECT_DELETION,

    /** The purpose this feature was collected for is no longer permitted. */
    CONSENT_WITHDRAWAL,

    /** The source data turned out to be wrong. */
    SOURCE_INVALIDATION,

    /** The feature definition itself was withdrawn. */
    DEFINITION_RETIREMENT
}
