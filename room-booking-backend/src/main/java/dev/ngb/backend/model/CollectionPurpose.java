package dev.ngb.backend.model;

/**
 * Why a guest owes this amount.
 *
 * <p>A booking gets a second obligation only by declaring a different purpose, which is what stops
 * a retry from quietly doubling what is owed.</p>
 */
public enum CollectionPurpose {
    /** The whole accepted price of the stay. */
    BOOKING_TOTAL,
    /** An up-front part of a scheduled collection. */
    DEPOSIT,
    /** The remainder of a scheduled collection, due later. */
    BALANCE,
    /** Extra owed because the booking changed after acceptance. */
    MODIFICATION_DELTA,
    /** An amount arising from a resolved claim against the stay. */
    DAMAGE_CLAIM
}
