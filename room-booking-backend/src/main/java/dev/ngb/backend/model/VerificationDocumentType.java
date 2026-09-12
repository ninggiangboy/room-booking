package dev.ngb.backend.model;

/**
 * Kind of document submitted as verification evidence.
 *
 * <p>Documents of every type are held by reference in encrypted storage with an explicit deletion
 * deadline; the database row carries a digest and a location, never the bytes.</p>
 */
public enum VerificationDocumentType {
    /** A passport. */
    PASSPORT,
    /** A government identity card. */
    NATIONAL_ID,
    /** A driving licence used as photographic identity. */
    DRIVING_LICENCE,
    /** A residence permit. */
    RESIDENCE_PERMIT,
    /** Evidence that a company is registered. */
    COMPANY_REGISTRATION,
    /** Evidence that the declared address is genuine. */
    PROOF_OF_ADDRESS,
    /** Evidence supporting ownership of a payout destination. */
    BANK_STATEMENT,
    /** A liveness capture taken during verification. */
    SELFIE
}
