package dev.ngb.backend.admin.internal.model.role;

/**
 * What kind of emergency justified opening access outside the ordinary path.
 */
public enum BreakGlassTrigger {

    /** A live incident nobody could resolve within ordinary authority. */
    INCIDENT,

    /** A system is down and the usual path to act on it is down with it. */
    OUTAGE,

    /** Somebody is at risk and waiting for the ordinary path is the greater harm. */
    SAFETY,

    /** A regulator required immediate action. */
    REGULATORY_ORDER,

    /** Losses are accruing faster than the ordinary path can stop them. */
    FRAUD_CONTAINMENT,

    /** Data has to be recovered and the routine tooling cannot reach it. */
    DATA_RECOVERY
}
