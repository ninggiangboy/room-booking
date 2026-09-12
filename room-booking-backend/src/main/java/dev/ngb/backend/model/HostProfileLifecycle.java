package dev.ngb.backend.model;

/**
 * How far a host's legal profile has progressed through verification.
 *
 * <p>{@link #EXPIRED} is distinct from {@link #REJECTED}: verification goes stale with time, and a
 * host whose evidence merely aged is in a different position from one whose evidence was refused.</p>
 */
public enum HostProfileLifecycle {
    /** Being filled in; not yet submitted for verification. */
    DRAFT,
    /** Submitted and under verification. */
    SUBMITTED,
    /** Verified; eligibility may be decided from the evidence. */
    VERIFIED,
    /** Verification was refused. */
    REJECTED,
    /** Previously verified, now stale and awaiting re-screening. */
    EXPIRED
}
