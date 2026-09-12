package dev.ngb.backend.model;

/**
 * Whether a line comes back to the guest when a stay is cancelled.
 *
 * <p>Recorded per line rather than per booking, because a stay routinely mixes all three: the
 * accommodation follows the cancellation policy, a service fee may be kept whatever happens, and a
 * deposit is always returned when nothing was damaged.</p>
 */
public enum Refundability {
    /** Always returned when the stay does not happen. */
    REFUNDABLE,
    /** Kept regardless of the cancellation outcome. */
    NON_REFUNDABLE,
    /** Decided by the cancellation policy in force for the booking. */
    POLICY_BASED
}
