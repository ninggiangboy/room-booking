package dev.ngb.backend.model;

/**
 * Current harm and urgency, not how angry a message sounds or how valuable the customer is.
 *
 * <p>Deterministic intake questions set a floor. Severity may rise on new evidence and may fall only
 * with an authorized reason, after any immediate danger has been addressed.</p>
 */
public enum CaseSeverity {

    /** Immediate or potentially severe physical safety threat; no automated downgrade. */
    S0_SAFETY_CRITICAL,

    /** Stranded, no access, uninhabitable stay, active account or payout compromise, or an expiring high-impact deadline. */
    S1_URGENT,

    /** Material stay or financial harm with a short remedy or provider window. */
    S2_HIGH,

    /** Ordinary service, complaint, claim, payment or payout issue. */
    S3_STANDARD,

    /** A question or low-impact record with no active loss. */
    S4_INFORMATIONAL
}
