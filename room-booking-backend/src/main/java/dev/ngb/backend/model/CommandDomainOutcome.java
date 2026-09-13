package dev.ngb.backend.model;

/**
 * What the owning domain did with a command it was handed.
 * <p>A refusal is a normal, expected outcome. An administrative console that can only record
 * successes is one whose logs make every denied attempt look like it never happened.</p>
 */
public enum CommandDomainOutcome {

    /** The domain made the change, and the row carries what it looked like before and after. */
    APPLIED,

    /** The domain found nothing to do. */
    NO_CHANGE,

    /** The domain declined under its own invariants. */
    REFUSED,

    /** Some of what was asked was done and the rest was not. */
    PARTIAL,

    /** The domain could not answer. */
    ERROR
}
