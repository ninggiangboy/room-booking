package dev.ngb.backend.analytics.internal.model.contract;

/**
 * Who or what recorded a correction.
 */
public enum CorrectionActorKind {

    /** A person. */
    OPERATOR,

    /** An erasure or suppression job. */
    PRIVACY_JOB,

    /** A transformation. */
    PIPELINE,

    /** The domain that owns the underlying fact. */
    SOURCE_DOMAIN
}
