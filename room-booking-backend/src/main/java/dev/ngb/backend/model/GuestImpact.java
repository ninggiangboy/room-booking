package dev.ngb.backend.model;

/**
 * How much a defect affects somebody staying there.
 *
 * <p>{@code UNUSABLE} and {@code UNSAFE} cannot leave triage without a calendar decision having
 * been taken, which is a check constraint rather than a service rule.</p>
 */
public enum GuestImpact {
    /** No guest would notice. */
    NONE,
    /** Visible but harmless. */
    COSMETIC,
    /** The stay is worse but workable. */
    DEGRADED,
    /** Part of the property cannot be used. */
    UNUSABLE,
    /** Somebody could be hurt. */
    UNSAFE
}
