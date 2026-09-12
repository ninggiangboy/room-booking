package dev.ngb.backend.model;

/**
 * Why a reserve exists.
 *
 * <p>A reserve is not an unbounded flag on a host. The basis is what makes it explainable, appealable,
 * and bounded by an approved policy rather than by an operator's judgement.</p>
 */
public enum HostReserveBasis {
    /** Held under an approved risk policy. */
    RISK_POLICY,
    /** Required by the host's contract. */
    CONTRACTUAL,
    /** Held against the value of disputes that could still arrive. */
    CHARGEBACK_EXPOSURE,
    /** Held while a new host builds a track record. */
    NEW_HOST_RAMP,
    /** Required by a regulator or a market rule. */
    REGULATORY,
    /** Placed by an operator under an approved exception. */
    MANUAL
}
