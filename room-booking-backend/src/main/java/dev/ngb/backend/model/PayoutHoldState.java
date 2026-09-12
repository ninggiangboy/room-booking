package dev.ngb.backend.model;

/**
 * Whether a hold still stops money.
 *
 * <p>A hold is replaced rather than deleted, so the reason money was ever held stays on the record
 * and a host can be told why a payout was late.</p>
 */
public enum PayoutHoldState {
    /** Currently blocking. */
    ACTIVE,
    /** Lifted deliberately, with a reason. */
    RELEASED,
    /** Lifted because its own expiry arrived. */
    EXPIRED,
    /** Superseded by another hold that names it. */
    REPLACED
}
