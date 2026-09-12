package dev.ngb.backend.model;

/**
 * What the platform decided about a host capability.
 *
 * <p>{@link #REVOKED} and {@link #SUSPENDED} are distinct: one withdraws a capability for good, the
 * other pauses it pending something — fresh evidence, an adjudication, an appeal — and a host needs
 * to know which of the two they are facing.</p>
 */
public enum EligibilityDecisionType {
    /** The capability is conferred, and a grant was written. */
    GRANTED,
    /** The capability was refused. */
    DENIED,
    /** A previously conferred capability was permanently withdrawn. */
    REVOKED,
    /** A previously conferred capability is paused pending resolution. */
    SUSPENDED
}
