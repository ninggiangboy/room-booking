package dev.ngb.backend.identity.internal.service.authz;

/**
 * The complete catalog of capability names the platform can grant or restrict.
 *
 * <p>This enum is the single source of truth for capability names. Migration {@code 014}'s
 * backfill copied a snapshot of this catalog into SQL because the Java catalog did not exist yet
 * when that migration was written; this enum is now the definition, and the SQL is the historical
 * copy. A {@link dev.ngb.backend.identity.internal.model.capability.CapabilityGrant} or {@link
 * dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction} that named a
 * capability outside this set could never be evaluated meaningfully, which is why every consumer
 * works from {@link #name()} rather than from a free-text column.</p>
 */
public enum Capability {

    // Guest bundle -- see RoleBundle#GUEST.
    /** May create a booking. */
    BOOKING_CREATE,
    /** May cancel a booking the principal made themselves. */
    BOOKING_CANCEL_OWN,
    /** May write a review for a stay the principal completed themselves. */
    REVIEW_WRITE_OWN,
    /** May send a message to another principal. */
    MESSAGE_SEND,

    // Host bundle -- see RoleBundle#HOST.
    /** May save a listing as a draft. */
    CAN_DRAFT,
    /** May publish a listing so it becomes publicly bookable. */
    CAN_PUBLISH,
    /** May accept an incoming booking request. */
    CAN_ACCEPT_BOOKING,
    /** May receive a payout for completed stays. */
    CAN_RECEIVE_PAYOUT,
    /** May manage listings the principal owns. */
    LISTING_MANAGE_OWN,
    /** May manage the calendar of listings the principal owns. */
    CALENDAR_MANAGE_OWN,

    // Admin bundle -- see RoleBundle#ADMIN.
    /** May suspend an account. */
    ACCOUNT_SUSPEND,
    /** May reactivate a suspended account. */
    ACCOUNT_REACTIVATE,
    /** May approve a pending configuration change. */
    CONFIGURATION_APPROVE,
    /** May manage a support case. */
    SUPPORT_CASE_MANAGE
}
