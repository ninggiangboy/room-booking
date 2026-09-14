package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * What kind of record an event is.
 *
 * <p>A committed fact, something observed, an operational signal and a governed derivation are
 * different claims. Commands and mutable snapshots are none of them.</p>
 */
public enum EventClass {

    /** A committed outcome a source domain accepted. */
    DOMAIN_FACT,

    /** A validated interface action the server could not observe itself. */
    INTERACTION_OBSERVATION,

    /** Signed or reconciled evidence from an external provider. */
    PROVIDER_OBSERVATION,

    /** Pipeline or service lifecycle evidence. */
    OPERATIONAL_EVENT,

    /** An additive invalidation, suppression or semantic correction. */
    CORRECTION,

    /** A governed result computed by the platform itself. */
    DERIVED_FACT
}
