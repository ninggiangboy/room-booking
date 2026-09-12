package dev.ngb.backend.model;

/**
 * Who or what stopped sending to a destination.
 */
public enum SuppressionSource {
    /** A hard bounce from the delivery provider. */
    PROVIDER_BOUNCE,
    /** A spam complaint from the delivery provider. */
    PROVIDER_COMPLAINT,
    /** A platform rule. */
    POLICY,
    /** An agent decision, with a case reference. */
    SUPPORT,
    /** A legal or regulatory requirement. */
    LEGAL
}
