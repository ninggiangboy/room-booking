package dev.ngb.backend.model;

/**
 * The validation state of {@code support_policy_versions}.
 */
public enum PolicyValidationState {

    /** Unvalidated. */
    UNVALIDATED,

    /** Validating. */
    VALIDATING,

    /** Valid. */
    VALID,

    /** Invalid. */
    INVALID
}
