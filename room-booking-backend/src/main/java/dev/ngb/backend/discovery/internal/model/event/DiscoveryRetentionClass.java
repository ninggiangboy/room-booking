package dev.ngb.backend.discovery.internal.model.event;

/**
 * How long an event may be kept before its own expiry deletes it.
 *
 * <p>Raw clickstream growth must not compete with booking transactions, so every row carries one.</p>
 */
public enum DiscoveryRetentionClass {

    /** Deleted soon after ingestion. */
    SHORT,

    /** The ordinary retention window. */
    STANDARD,

    /** Kept longer under a documented purpose. */
    EXTENDED,

    /** Exempt from retention deletion while the hold stands. */
    LEGAL_HOLD
}
