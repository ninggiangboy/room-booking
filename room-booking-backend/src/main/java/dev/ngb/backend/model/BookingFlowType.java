package dev.ngb.backend.model;

/**
 * How a booking request becomes a contract.
 *
 * <p>The distinction is not cosmetic. {@link #INSTANT} confirms inside the claim transaction, so the
 * guest either holds the nights or does not. {@link #REQUEST} confirms only after a host decision,
 * which means the hold must survive a human response time and can expire without anyone acting.</p>
 */
public enum BookingFlowType {
    /** The guest's acceptance confirms the stay directly. */
    INSTANT,
    /** A host decision stands between acceptance and confirmation. */
    REQUEST
}
