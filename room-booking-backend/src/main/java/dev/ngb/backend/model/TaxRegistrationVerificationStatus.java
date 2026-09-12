package dev.ngb.backend.model;

/**
 * Whether a tax registration has been confirmed with an authority.
 *
 * <p>{@code INVALID} and {@code EXPIRED} are kept apart because they call for different conversations
 * with the party: one was never right, the other has simply lapsed and can usually be renewed.</p>
 */
public enum TaxRegistrationVerificationStatus {
    /** Supplied but not checked. */
    UNVERIFIED,
    /** Confirmed against an accepted source. */
    VERIFIED,
    /** Checked and rejected. */
    INVALID,
    /** Was valid; no longer is. */
    EXPIRED
}
