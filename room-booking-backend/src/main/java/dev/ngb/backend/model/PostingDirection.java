package dev.ngb.backend.model;

/**
 * Which side of the journal an amount lands on.
 *
 * <p>Posting amounts are always positive and carry their direction explicitly. A debit and a credit
 * are different facts, not the same number with opposite signs, and a bare negative in a money
 * column is an invitation to read it the wrong way.</p>
 */
public enum PostingDirection {
    /** Increases an asset or expense; decreases a liability, equity, or revenue. */
    DEBIT,
    /** Increases a liability, equity, or revenue; decreases an asset or expense. */
    CREDIT
}
