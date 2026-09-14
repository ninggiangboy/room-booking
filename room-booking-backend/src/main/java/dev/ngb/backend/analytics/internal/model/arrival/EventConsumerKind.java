package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * What sort of component reads an event contract.
 */
public enum EventConsumerKind {

    /** A running service. */
    SERVICE,

    /** A transformation. */
    PIPELINE,

    /** An approved export or research dataset. */
    EXPORT,

    /** An experiment analysis that depends on the contract. */
    EXPERIMENT
}
