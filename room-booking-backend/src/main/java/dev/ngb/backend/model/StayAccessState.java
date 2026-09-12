package dev.ngb.backend.model;

/**
 * Whether the guest can get in.
 *
 * <p>Derived from the grants below the stay, and deliberately separate from readiness.</p>
 */
public enum StayAccessState {
    /** Entry needs no platform-issued access. */
    NOT_REQUIRED,
    /** A grant exists but no credential is live yet. */
    PENDING,
    /** At least one grant can open the door. */
    ACTIVE,
    /** Provisioning failed and a fallback is needed. */
    FAILED,
    /** Access was withdrawn. */
    REVOKED,
    /** Validity has passed. */
    EXPIRED
}
