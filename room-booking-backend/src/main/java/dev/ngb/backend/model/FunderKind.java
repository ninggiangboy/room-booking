package dev.ngb.backend.model;

/**
 * Whose money a line moves.
 *
 * <p>Host-funded, platform-funded, insurer-funded, partner-funded and guest-funded values never silently
 * substitute for one another.</p>
 */
public enum FunderKind {

    /** Room Booking funds it. */
    PLATFORM,

    /** The host funds it, from value a host can legally control. */
    HOST,

    /** The guest funds it. */
    GUEST,

    /** A carrier or protection provider funds it. */
    INSURER,

    /** A commercial partner funds it. */
    PARTNER
}
