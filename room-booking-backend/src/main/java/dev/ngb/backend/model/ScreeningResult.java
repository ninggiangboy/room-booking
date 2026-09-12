package dev.ngb.backend.model;

/**
 * What a screening run returned.
 *
 * <p>A {@link #POTENTIAL_MATCH} is a question, not an answer: names collide, and a person must
 * adjudicate before any capability decision reads the result. {@link #ERROR} is kept distinct from
 * {@link #CLEAR} so a failed screening can never be mistaken for a passed one.</p>
 */
public enum ScreeningResult {
    /** No matches found. */
    CLEAR,
    /** One or more possible matches needing adjudication. */
    POTENTIAL_MATCH,
    /** A match the provider asserts is genuine, still needing adjudication. */
    CONFIRMED_MATCH,
    /** The screening could not be completed; never treat as clear. */
    ERROR
}
