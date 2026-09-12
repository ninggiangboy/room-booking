package dev.ngb.backend.model;

/**
 * Whether a host's manual price instruction still applies.
 *
 * <p>Only {@code ACTIVE} overrides participate in the no-overlap exclusion constraint, so replacing
 * an override means withdrawing the old one first rather than editing it — which keeps the record of
 * what was charged, and by whose instruction.</p>
 */
public enum PriceOverrideStatus {
    /** In force; outranks every rule for its nights. */
    ACTIVE,
    /** Withdrawn by a host or agent; the computed price applies again. */
    WITHDRAWN,
    /** Replaced by a later override covering the same nights. */
    SUPERSEDED
}
