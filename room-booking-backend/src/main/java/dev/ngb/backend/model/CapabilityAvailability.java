package dev.ngb.backend.model;

/**
 * Whether a capability, payment method, or payout rail may be used in a market.
 *
 * <p>The absence of a row means the same thing as {@code DISABLED}: capability is granted
 * explicitly, so a market that has not been configured cannot accidentally inherit one.</p>
 */
public enum CapabilityAvailability {
    /** Not available. */
    DISABLED,
    /** Available only to traffic the eligibility rule selects. */
    PILOT,
    /** Generally available. */
    ENABLED,
    /** Withdrawn after having been available; history remains valid. */
    SUSPENDED
}
