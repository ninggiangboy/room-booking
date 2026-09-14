package dev.ngb.backend.stay.internal.model.stay;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * What operations currently believes the stay should become.
 *
 * <p>A proposal. Booking performs the guarded transition and owns the committed fact.</p>
 */
public enum StayOutcomeProposal {
    /** Not yet evaluated. */
    PENDING,
    /** Evidence and policy support completion. */
    COMPLETION_ELIGIBLE,
    /** Absence is claimed and needs review. */
    NO_SHOW_REVIEW,
    /** The booking was cancelled before or during the stay. */
    CANCELLED,
    /** Evidence conflicts or a material case is open. */
    EXCEPTION
}
