package dev.ngb.backend.model;

/**
 * The sample cohort of {@code case_quality_reviews}.
 */
public enum QualitySampleCohort {

    /** Random. */
    RANDOM,

    /** High severity. */
    HIGH_SEVERITY,

    /** High amount. */
    HIGH_AMOUNT,

    /** Policy exception. */
    POLICY_EXCEPTION,

    /** Appealed. */
    APPEALED,

    /** Reversed. */
    REVERSED,

    /** Repeat contact. */
    REPEAT_CONTACT,

    /** Accessibility context. */
    ACCESSIBILITY_CONTEXT,

    /** New agent. */
    NEW_AGENT,

    /** Customer harm. */
    CUSTOMER_HARM
}
