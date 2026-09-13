package dev.ngb.backend.model;

/**
 * The journey stage of {@code support_cases}.
 */
public enum CaseJourneyStage {

    /** Pre booking. */
    PRE_BOOKING,

    /** Checkout. */
    CHECKOUT,

    /** Pre stay. */
    PRE_STAY,

    /** Check in. */
    CHECK_IN,

    /** In stay. */
    IN_STAY,

    /** Check out. */
    CHECK_OUT,

    /** Post stay. */
    POST_STAY,

    /** Payout. */
    PAYOUT,

    /** Not applicable. */
    NOT_APPLICABLE
}
