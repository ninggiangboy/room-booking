package dev.ngb.backend.model;

/**
 * Whether the guest has agreed to be moved.
 *
 * <p>{@code NOT_REACHABLE} is recorded rather than treated as refusal, because a guest who cannot be
 * reached at midnight still needs somewhere to sleep, and the case has to be able to say which of the
 * two happened.</p>
 */
public enum RelocationConsentState {
    /** Asked and not yet answered. */
    PENDING,
    /** Agreed, with the instant recorded. */
    GIVEN,
    /** The guest would rather be refunded. */
    REFUSED,
    /** Contact could not be made in the time available. */
    NOT_REACHABLE
}
