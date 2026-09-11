package dev.ngb.backend.model;

/**
 * Handling class of an event payload.
 *
 * <p>The class travels with the fact so that downstream transports, analytical pipelines, and
 * retention rules can decide what they are permitted to store and expose without re-inspecting the
 * payload.</p>
 */
public enum SensitivityClass {
    /** Safe to expose outside the platform. */
    PUBLIC,
    /** Safe within the platform, not for external delivery. */
    INTERNAL,
    /** Restricted to the owning domain and approved consumers. */
    CONFIDENTIAL,
    /** Requires explicit authorization for every consumer. */
    RESTRICTED
}
