package dev.ngb.backend.model;

/**
 * How an obligation is meant to be collected.
 *
 * <p>Chosen from accepted terms when the obligation is created, not by the caller at payment
 * time.</p>
 */
public enum CapturePolicy {
    /** Authorise and capture as one provider action. */
    SALE,
    /** Reserve now, capture when the booking's confirmation condition holds. */
    AUTHORIZE_THEN_CAPTURE,
    /** Reserve now, capture only on an explicit operator command. */
    MANUAL_CAPTURE
}
