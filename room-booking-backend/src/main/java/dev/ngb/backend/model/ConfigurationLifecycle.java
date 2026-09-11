package dev.ngb.backend.model;

/**
 * Lifecycle shared by markets, legal entities, and provider accounts.
 *
 * <p>Configuration is never deleted. A retired market still has to explain the bookings it
 * governed, so the row stays and its state changes instead.</p>
 */
public enum ConfigurationLifecycle {
    /** Being prepared; not usable by any domain decision. */
    DRAFT,
    /** Approved and usable. */
    ACTIVE,
    /** Temporarily withdrawn; history remains valid, new use is refused. */
    SUSPENDED,
    /** Permanently withdrawn. */
    RETIRED
}
