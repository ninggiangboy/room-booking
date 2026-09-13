package dev.ngb.backend.model;

/**
 * How long an observation may be kept, and why.
 *
 * <p>Transient device and network observations must carry an expiry, which is what stops them
 * accumulating into a covert permanent identity.</p>
 */
public enum RiskRetentionClass {
    /** Short-lived telemetry; must carry an expiry. */
    TRANSIENT,
    /** Kept briefly for operational purposes. */
    SHORT_TERM,
    /** Kept while the operational purpose lasts. */
    OPERATIONAL,
    /** Kept for the life of the contract it concerns. */
    CONTRACTUAL,
    /** Kept for a period a regulator requires. */
    REGULATORY,
    /** Kept as evidence in a safety matter. */
    SAFETY_EVIDENCE,
    /** Held under a scoped, authorized legal hold. */
    LEGAL_HOLD;
}
