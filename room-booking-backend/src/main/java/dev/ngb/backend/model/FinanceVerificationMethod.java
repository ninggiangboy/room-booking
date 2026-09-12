package dev.ngb.backend.model;

/**
 * How a piece of financial evidence was authenticated.
 *
 * <p>Evidence that was not verified is not evidence. A webhook in particular is only trustworthy when
 * its signature was checked against a known key version, which the database insists on.</p>
 */
public enum FinanceVerificationMethod {
    /** Signature checked against a named provider key version. */
    WEBHOOK_SIGNATURE,
    /** Fetched by the platform over an authenticated channel. */
    AUTHENTICATED_API,
    /** A report file whose signature or checksum was verified. */
    SIGNED_FILE,
    /** Received over a controlled banking channel. */
    BANK_CHANNEL,
    /** Recorded by a named operator who vouches for it. */
    OPERATOR_ATTESTED
}
