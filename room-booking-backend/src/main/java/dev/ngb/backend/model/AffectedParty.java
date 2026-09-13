package dev.ngb.backend.model;

/**
 * Which side of the marketplace a slice measures.
 *
 * <p>An aggregate that looks fine can hide a guest-side error rate and a host-side exposure effect
 * pulling in opposite directions.</p>
 */
public enum AffectedParty {

    /** The guest side. */
    GUEST,

    /** The host side. */
    HOST,

    /** Both sides, measured together. */
    BOTH
}
