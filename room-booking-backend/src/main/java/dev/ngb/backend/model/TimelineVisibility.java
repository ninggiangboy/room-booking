package dev.ngb.backend.model;

/**
 * Who may see a booking timeline entry.
 *
 * <p>Mandatory on every entry, with no default that leans open. Internal notes and guest-facing
 * updates share one table, and an entry whose audience is merely implied is how a support note ends
 * up rendered in a guest's itinerary.</p>
 */
public enum TimelineVisibility {
    /** The guest only. */
    GUEST,
    /** The host only. */
    HOST,
    /** Both parties to the booking. */
    BOTH,
    /** Platform staff only; never rendered to a guest or host. */
    INTERNAL
}
