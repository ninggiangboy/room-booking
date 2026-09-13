package dev.ngb.backend.model;

/**
 * The submission kind of {@code external_claim_submissions}.
 */
public enum ExternalSubmissionKind {

    /** Initial. */
    INITIAL,

    /** Additional information. */
    ADDITIONAL_INFORMATION,

    /** Correction. */
    CORRECTION,

    /** Appeal. */
    APPEAL,

    /** Withdrawal. */
    WITHDRAWAL
}
