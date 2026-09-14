package dev.ngb.backend.stay.internal.model.instruction;

/**
 * The sensitivity band an instruction field belongs to.
 *
 * <p>Release conditions are evaluated per band, which is why the bands are stored separately.</p>
 */
public enum InstructionFieldClass {
    /** Arrival window, preparation checklist, host contact method. */
    PUBLIC_EARLY,
    /** Approximate directions and approved house reminders. */
    CONFIRMED_BOOKING,
    /** Exact address, unit or floor, entry route, meeting point. */
    TIME_GATED,
    /** Access code or token and fallback verification data. */
    SECRET,
    /** Appliances, parking, network, safety equipment, checkout details. */
    POST_ENTRY
}
