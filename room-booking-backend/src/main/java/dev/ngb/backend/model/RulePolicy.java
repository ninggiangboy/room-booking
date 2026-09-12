package dev.ngb.backend.model;

/**
 * Whether a house rule permits something, refuses it, or requires asking first.
 *
 * <p>{@link #ON_REQUEST} is a real third answer rather than a soft refusal: a guest travelling with a
 * pet needs to know whether to ask, and collapsing it into "not allowed" loses bookings that would
 * have been fine.</p>
 */
public enum RulePolicy {
    /** Permitted without asking. */
    ALLOWED,
    /** Permitted only with the host's agreement. */
    ON_REQUEST,
    /** Not permitted. */
    NOT_ALLOWED
}
