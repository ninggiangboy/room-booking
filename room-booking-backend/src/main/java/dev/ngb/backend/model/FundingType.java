package dev.ngb.backend.model;

/**
 * Where the money behind an instrument comes from.
 *
 * <p>Safe display metadata. Stored only where product or support policy needs it.</p>
 */
public enum FundingType {
    /** Drawn on a line of credit. */
    CREDIT,
    /** Drawn directly on an account. */
    DEBIT,
    /** Drawn on a pre-funded balance. */
    PREPAID,
    /** The provider did not say. */
    UNKNOWN
}
