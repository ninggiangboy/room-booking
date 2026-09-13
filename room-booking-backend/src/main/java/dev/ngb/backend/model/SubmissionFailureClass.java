package dev.ngb.backend.model;

/**
 * The failure classification of {@code external_claim_submissions}.
 */
public enum SubmissionFailureClass {

    /** Transient. */
    TRANSIENT,

    /** Permanent input. */
    PERMANENT_INPUT,

    /** Permanent authorization. */
    PERMANENT_AUTHORIZATION,

    /** Provider error. */
    PROVIDER_ERROR
}
