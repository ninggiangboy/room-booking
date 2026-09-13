package dev.ngb.backend.model;

/**
 * The beneficiary kind of {@code case_offer_lines}.
 */
public enum BeneficiaryKind {

    /** The guest receives it. */
    GUEST,

    /** The host receives it. */
    HOST,

    /** Room Booking receives it. */
    PLATFORM,

    /** Somebody outside the booking receives it. */
    THIRD_PARTY
}
