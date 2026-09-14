package dev.ngb.backend.growth.internal.model.affiliate;

/**
 * Where one affiliate click stands between arriving and being credited.
 */
public enum AffiliateAttributionState {

    /** A click inside its window, with no booking yet. */
    OPEN,

    /** A booking has been credited to it. */
    ATTRIBUTED,

    /** The window closed with no booking. */
    EXPIRED,

    /** Refused, for a recorded reason. */
    REJECTED
}
