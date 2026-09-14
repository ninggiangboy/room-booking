package dev.ngb.backend.support.internal.model.case_;

/**
 * The waiting on of {@code support_cases}.
 */
public enum CaseWaitingOn {

    /** None. */
    NONE,

    /** Customer. */
    CUSTOMER,

    /** Host. */
    HOST,

    /** Agent. */
    AGENT,

    /** Third party. */
    THIRD_PARTY,

    /** Provider. */
    PROVIDER,

    /** Internal domain. */
    INTERNAL_DOMAIN,

    /** Legal. */
    LEGAL
}
