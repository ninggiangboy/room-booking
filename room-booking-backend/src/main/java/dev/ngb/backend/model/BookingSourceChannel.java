package dev.ngb.backend.model;

/**
 * Where a booking entered the platform.
 *
 * <p>Kept because obligations differ by origin: a stay imported from a channel manager was never
 * shown the platform's own terms, and a booking created by support was agreed to somewhere other
 * than a screen the platform controls.</p>
 */
public enum BookingSourceChannel {
    /** The platform's own web experience. */
    WEB,
    /** The platform's iOS application. */
    IOS,
    /** The platform's Android application. */
    ANDROID,
    /** A partner integrating over the public API. */
    PARTNER_API,
    /** A support agent acting on a guest's behalf. */
    SUPPORT,
    /** An external distribution system that owns its own booking flow. */
    CHANNEL_MANAGER
}
