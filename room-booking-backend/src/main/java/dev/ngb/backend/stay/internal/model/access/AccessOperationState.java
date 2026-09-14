package dev.ngb.backend.stay.internal.model.access;

/**
 * How far one provider call has got.
 *
 * <p>The submission fence is a check constraint: only a call that never reached the wire may be
 * cancelled, and a timeout resolves to {@code UNKNOWN} rather than a failure.</p>
 */
public enum AccessOperationState {
    /** Not yet submitted. */
    PLANNED,
    /** On the wire. */
    SUBMITTING,
    /** Accepted by the provider and still working. */
    PENDING,
    /** The provider confirmed it. */
    SUCCEEDED,
    /** The provider refused it. */
    FAILED,
    /** The outcome is not known and must be queried. */
    UNKNOWN,
    /** Abandoned before anything left the platform. */
    CANCELLED_BEFORE_SUBMISSION
}
