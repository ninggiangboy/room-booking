package dev.ngb.backend.model;

/**
 * Why a one-time token stopped being usable.
 *
 * <p>Without this, normal rotation and an attacker replaying a stolen token produce identical rows,
 * and reuse detection has nothing to detect. {@link #REUSE_DETECTED} is the signal that a whole
 * session's lineage must be revoked.</p>
 */
public enum TokenConsumptionReason {
    /** Redeemed for its intended one-time purpose. */
    USED,
    /** Exchanged for a successor token in the normal way. */
    ROTATED,
    /** Withdrawn by the owner or an operator. */
    REVOKED,
    /** Presented after it had already been rotated, indicating theft. */
    REUSE_DETECTED,
    /** Passed its expiry without being redeemed. */
    EXPIRED,
    /** Invalidated because the session was ended deliberately. */
    LOGOUT
}
