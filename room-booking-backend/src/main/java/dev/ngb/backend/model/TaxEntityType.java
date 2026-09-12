package dev.ngb.backend.model;

/**
 * Whether a party is taxed as a person or as a business.
 *
 * <p>It changes almost everything downstream: which registrations are expected, whether withholding
 * applies, and what has to be reported about them. It is never inferred from having supplied a
 * registration number.</p>
 */
public enum TaxEntityType {
    /** A natural person. */
    INDIVIDUAL,
    /** A company, partnership, or other registered undertaking. */
    BUSINESS
}
