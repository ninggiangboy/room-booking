package dev.ngb.backend.model;

/**
 * Who actually provides what a line pays for.
 *
 * <p>Separate from who receives the money. A cleaning fee can be supplied by a third-party company,
 * collected by the platform, and settled to the host — and tax treatment can depend on which of those
 * is true.</p>
 */
public enum SupplyRole {
    /** The host provides it as part of the stay. */
    HOST,
    /** The platform provides it as a service. */
    PLATFORM,
    /** An external provider supplies it. */
    THIRD_PARTY
}
