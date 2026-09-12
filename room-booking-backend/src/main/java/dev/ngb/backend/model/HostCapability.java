package dev.ngb.backend.model;

/**
 * The four capabilities host verification can confer or withhold.
 *
 * <p>They are separate because a host can legitimately hold some and not others: someone may draft
 * and publish while payout is still held pending bank verification, or keep an existing booking
 * obligation after publication is suspended. Collapsing them into one "verified" flag would force
 * the platform to choose between blocking honest hosts and paying unverified ones.</p>
 *
 * <p>These names are written into {@code capability_grants}, which remains the single authority the
 * rest of the platform evaluates.</p>
 */
public enum HostCapability {
    /** May create and edit unpublished supply. */
    CAN_DRAFT,
    /** May make supply publicly visible and bookable. */
    CAN_PUBLISH,
    /** May take on new booking obligations. */
    CAN_ACCEPT_BOOKING,
    /** May have settled funds sent to them. */
    CAN_RECEIVE_PAYOUT
}
