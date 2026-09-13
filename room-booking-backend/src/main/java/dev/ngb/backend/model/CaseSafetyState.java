package dev.ngb.backend.model;

/**
 * The safety state of {@code support_cases}.
 */
public enum CaseSafetyState {

    /** Not applicable. */
    NOT_APPLICABLE,

    /** Screening. */
    SCREENING,

    /** Guidance given. */
    GUIDANCE_GIVEN,

    /** Escalated. */
    ESCALATED,

    /** Handed off. */
    HANDED_OFF,

    /** Resolved. */
    RESOLVED
}
