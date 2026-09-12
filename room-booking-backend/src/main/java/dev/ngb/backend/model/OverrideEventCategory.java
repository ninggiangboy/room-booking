package dev.ngb.backend.model;

/**
 * The kind of event a policy override programme responds to.
 *
 * <p>Categorised because the evidence bar, the approval chain and the funding default differ by kind.
 * A typhoon and a platform outage are both reasons to waive a fee, and they are not the same reason.</p>
 */
public enum OverrideEventCategory {
    /** Earthquake, flood, storm and the like. */
    NATURAL_DISASTER,
    /** Outbreak or public health order. */
    PUBLIC_HEALTH,
    /** Unrest making travel unsafe. */
    CIVIL_UNREST,
    /** A border or movement restriction. */
    TRAVEL_RESTRICTION,
    /** The platform's own failure. */
    PLATFORM_INCIDENT,
    /** A systemic failure of supply, beyond one property. */
    SUPPLY_FAILURE,
    /** A safety concern about specific supply. */
    SAFETY,
    /** A discretionary programme with no external event. */
    GOODWILL,
    /** A legal order or settlement. */
    LEGAL
}
