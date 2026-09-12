package dev.ngb.backend.model;

/**
 * How far a claimed regulatory registration has been checked.
 *
 * <p>{@link #NOT_REQUIRED} is recorded explicitly rather than left as a missing row, so that "this
 * jurisdiction does not require a permit" is distinguishable from "nobody has checked yet".</p>
 */
public enum RegulatoryRegistrationStatus {
    /** Claimed by the host, unverified. */
    DECLARED,
    /** Confirmed against the issuing authority. */
    VERIFIED,
    /** Checked and refused. */
    REJECTED,
    /** Was valid and has lapsed. */
    EXPIRED,
    /** The jurisdiction does not require this registration. */
    NOT_REQUIRED
}
