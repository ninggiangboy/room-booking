package dev.ngb.backend.model;

/**
 * Who bears the cost of what a growth programme hands out.
 *
 * <p>Nothing is granted without one of these, because value that names no funder is a liability
 * the finance close will never find.</p>
 */
public enum GrowthFunderType {

    /** The platform pays. */
    PLATFORM,

    /** The host pays out of what they would have earned. */
    HOST,

    /** An outside partner pays. */
    PARTNER,

    /** The cost is split, and the split is in the terms. */
    MIXED
}
