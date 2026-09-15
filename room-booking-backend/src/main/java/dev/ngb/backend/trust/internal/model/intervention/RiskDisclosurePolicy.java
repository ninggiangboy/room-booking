package dev.ngb.backend.trust.internal.model.intervention;

/**
 * How much of a decision its subject is told.
 *
 * <p>User-facing explanations use approved reason families and never expose detector detail.
 * Withholding is permitted only where disclosure would itself create a documented hazard.</p>
 */
public enum RiskDisclosurePolicy {
    /** The full internal reason may be shown. */
    FULL_REASON,
    /** An approved reason family is shown. */
    REASON_FAMILY,
    /** Only that the action could not proceed. */
    MINIMAL,
    /** Withheld because disclosure would create a safety hazard. */
    WITHHELD_SAFETY,
    /** Withheld because disclosure is legally prohibited. */
    WITHHELD_LEGAL
}
