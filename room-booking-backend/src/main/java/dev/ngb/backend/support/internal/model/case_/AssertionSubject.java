package dev.ngb.backend.support.internal.model.case_;

/**
 * The assertion subject of {@code case_assertions}.
 */
public enum AssertionSubject {

    /** Occurrence. */
    OCCURRENCE,

    /** Condition before stay. */
    CONDITION_BEFORE_STAY,

    /** Condition after stay. */
    CONDITION_AFTER_STAY,

    /** Causation. */
    CAUSATION,

    /** Responsibility. */
    RESPONSIBILITY,

    /** Value. */
    VALUE,

    /** Prior disclosure. */
    PRIOR_DISCLOSURE,

    /** Access. */
    ACCESS,

    /** Communication. */
    COMMUNICATION,

    /** Payment. */
    PAYMENT,

    /** Identity. */
    IDENTITY,

    /** Other. */
    OTHER
}
