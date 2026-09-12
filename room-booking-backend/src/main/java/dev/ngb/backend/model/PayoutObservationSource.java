package dev.ngb.backend.model;

/**
 * How a piece of payout evidence reached the platform.
 */
public enum PayoutObservationSource {
    /** Pushed by the provider and signature-verified. */
    WEBHOOK,
    /** Pulled by the platform over an authenticated channel. */
    API_QUERY,
    /** Read from a provider report or settlement file. */
    REPORT_FILE,
    /** Read from a bank statement or feed. */
    BANK_FEED,
    /** Entered by an operator, attested rather than machine-verified. */
    MANUAL
}
