package dev.ngb.backend.model;

/**
 * The surface on which a policy acceptance was collected.
 *
 * <p>Part of the evidence, not decoration. An acceptance recorded through {@code SUPPORT} was taken
 * by an agent rather than clicked by the guest, and that difference is exactly what a later dispute
 * turns on.</p>
 */
public enum AcceptanceChannel {
    /** The platform's own web experience. */
    WEB,
    /** The platform's iOS application. */
    IOS,
    /** The platform's Android application. */
    ANDROID,
    /** A partner integrating over the public API. */
    PARTNER_API,
    /** A support agent acting on the guest's behalf. */
    SUPPORT
}
