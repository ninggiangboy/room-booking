package dev.ngb.backend.model;

/**
 * Who ended the booking.
 *
 * <p>Recorded because the consequences diverge sharply: a host cancellation triggers remediation and
 * relocation duties the platform owes the guest, while a guest cancellation runs the cancellation
 * policy the guest accepted. A cancellation that cannot name its author cannot be judged, which is
 * why {@code ck_bookings_cancelled_by} requires one.</p>
 */
public enum CancellingParty {
    /** The guest ended the stay. */
    GUEST,
    /** The host ended the stay. */
    HOST,
    /** The platform ended it, for policy, safety, or trust reasons. */
    PLATFORM,
    /** An automated process ended it, typically an expired hold. */
    SYSTEM
}
