package dev.ngb.backend.model;

/**
 * The lawful basis a communication rests on.
 */
public enum ConsentLegalBasis {
    /** The recipient agreed. */
    CONSENT,
    /** Necessary to perform the booking contract. */
    CONTRACT,
    /** Balanced interest, where the market permits it. */
    LEGITIMATE_INTEREST,
    /** Required by law. */
    LEGAL_OBLIGATION
}
