package dev.ngb.backend.model;

/**
 * Where a guest’s place in a waitlist stands.
 *
 * <p>An entry only reaches {@code OFFERED} with a quote holding the inventory behind it, so
 * nobody is told a room opened up that was never theirs.</p>
 */
public enum WaitlistEntryState {

    /** In the queue. */
    WAITING,

    /** Offered a room that a quote is holding. */
    OFFERED,

    /** Took the offer and booked. */
    CONVERTED,

    /** The entry ran out. */
    EXPIRED,

    /** Left the queue, for a recorded reason. */
    WITHDRAWN
}
