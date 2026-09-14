package dev.ngb.backend.discovery.internal.model.event;

/**
 * What happened to an event at the ingestion boundary.
 *
 * <p>A duplicate is discarded rather than counted twice, and anything rejected or quarantined owes a
 * stated reason.</p>
 */
public enum DiscoveryIngestState {

    /** Ingested and available to derived jobs. */
    ACCEPTED,

    /** A retry of an event already ingested under the same key. */
    DUPLICATE_DISCARDED,

    /** Failed validation and was not ingested. */
    REJECTED,

    /** Held aside as implausible pending review, rather than trusted or discarded. */
    QUARANTINED
}
