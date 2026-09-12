package dev.ngb.backend.model;

/**
 * How far a declared tax identifier has been checked.
 *
 * <p>{@link #DECLARED} means the host typed it and nothing has confirmed it. Treating a declaration
 * as a validation is how withholding gets applied at the wrong rate for a year before anyone
 * notices.</p>
 */
public enum TaxRegistrationStatus {
    /** Supplied by the host, unverified. */
    DECLARED,
    /** Confirmed against the issuing authority. */
    VALIDATED,
    /** Checked and found not to be valid. */
    INVALID,
    /** Was valid and has lapsed. */
    EXPIRED
}
