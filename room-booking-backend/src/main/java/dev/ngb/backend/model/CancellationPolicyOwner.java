package dev.ngb.backend.model;

/**
 * Who owns a cancellation policy family.
 *
 * <p>Ownership decides who may publish a new version and who answers for the terms. A host-owned
 * family names its host; a market-owned one names its market.</p>
 */
public enum CancellationPolicyOwner {
    /** Defined centrally and offered everywhere. */
    PLATFORM,
    /** Defined for one market, usually to satisfy local law. */
    MARKET,
    /** Defined by a host for their own supply. */
    HOST
}
