package dev.ngb.backend.model;

/**
 * How a temporary inventory hold ended, or that it has not.
 *
 * <p>{@link #CONSUMED} is distinct from {@link #RELEASED}: one became a booking, the other gave the
 * nights back. Collapsing them would make it impossible to tell a successful checkout from an
 * abandoned one after the fact.</p>
 */
public enum HoldStatus {
    /** Still holding the nights. */
    ACTIVE,
    /** Turned into a booking. */
    CONSUMED,
    /** Lapsed at its expiry without being used. */
    EXPIRED,
    /** Given up deliberately before expiry. */
    RELEASED
}
