package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * What ordering guarantee an event contract offers.
 *
 * <p>Global ordering is neither assumed nor required; an aggregate version is what establishes
 * local source order when the domain exposes one.</p>
 */
public enum OrderingSemantics {

    /** No ordering is offered. */
    NONE,

    /** Ordered within one aggregate by its version. */
    AGGREGATE_VERSION,

    /** Ordered by a sequence the producer maintains. */
    PRODUCER_SEQUENCE
}
