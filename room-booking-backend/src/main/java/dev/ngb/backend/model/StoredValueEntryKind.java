package dev.ngb.backend.model;

/**
 * What kind of movement one stored-value entry records.
 *
 * <p>Reservation and release move nothing off the balance and post nothing; every kind that does
 * move the balance cites the ledger transaction behind it.</p>
 */
public enum StoredValueEntryKind {

    /** Value added to the balance. */
    GRANT,

    /** Value set aside against an open quote, still on the balance. */
    RESERVE,

    /** A reservation given back. */
    RELEASE,

    /** Reserved value actually spent on a booking. */
    REDEEM,

    /** Value removed because its lot expired. */
    EXPIRE,

    /** Value removed by decision. */
    REVOKE,

    /** Unspent value recognised as revenue. */
    BREAKAGE,

    /** Value moved in from another balance. */
    TRANSFER_IN,

    /** Value moved out to another balance. */
    TRANSFER_OUT,

    /** A correction naming the movement it undoes. */
    REVERSE
}
