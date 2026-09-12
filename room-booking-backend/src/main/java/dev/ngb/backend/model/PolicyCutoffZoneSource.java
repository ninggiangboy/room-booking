package dev.ngb.backend.model;

/**
 * Where the time zone behind a cutoff comes from.
 *
 * <p>Separate from {@link PolicyCutoffBasis}: the basis says which clock, this says how that clock is
 * resolved when the row is evaluated.</p>
 */
public enum PolicyCutoffZoneSource {
    /** The property's configured IANA zone. */
    PROPERTY,
    /** The market's default zone, for supply that has not set its own. */
    MARKET,
    /** The contracting entity's zone, where regulation fixes it. */
    LEGAL_ENTITY,
    /** A zone named on the policy version itself. */
    EXPLICIT
}
