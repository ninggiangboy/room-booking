package dev.ngb.backend.model;

/**
 * Where a piece of external financial evidence came from.
 */
public enum ExternalArtifactSource {
    /** A settlement or transaction report produced by a payment provider. */
    PROVIDER_REPORT,
    /** A response read from a provider API. */
    PROVIDER_API,
    /** A statement file from a bank. */
    BANK_STATEMENT,
    /** A continuous feed from a bank. */
    BANK_FEED,
    /** A file an operator supplied. */
    MANUAL_UPLOAD
}
