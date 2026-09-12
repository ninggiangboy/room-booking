package dev.ngb.backend.model;

/**
 * How much proof an override programme demands before it will judge a booking.
 *
 * <p>Stored on the programme version rather than decided per case, so that two guests applying to the
 * same programme on the same day face the same bar.</p>
 */
public enum OverrideEvidenceRequirement {
    /** No evidence; eligibility follows from the booking itself. */
    NONE,
    /** The applicant's own statement. */
    SELF_DECLARED,
    /** A document the applicant supplies. */
    DOCUMENTARY,
    /** A source the platform trusts independently, such as a met office. */
    AUTHORITATIVE
}
