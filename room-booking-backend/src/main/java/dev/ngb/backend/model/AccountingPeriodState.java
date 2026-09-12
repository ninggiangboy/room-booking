package dev.ngb.backend.model;

/**
 * How firmly a financial period is closed.
 *
 * <p>A hard close is the claim that the numbers are final, so it carries the watermark, balance hash,
 * and approvals that made it final. A reopen is rare, separately authorised, and recorded beside the
 * close rather than in place of it.</p>
 */
public enum AccountingPeriodState {
    /** Accepting postings. */
    OPEN,
    /** Close controls are running; postings may still be accepted under policy. */
    SOFT_CLOSING,
    /** Final, with its evidence and sign-off recorded. */
    HARD_CLOSED,
    /** Reopened under approval, pending a re-close with a new version. */
    REOPENED
}
