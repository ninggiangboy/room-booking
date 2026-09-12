package dev.ngb.backend.model;

/**
 * How far a booking's disclosure has progressed.
 *
 * <p>Monotonic, enforced by trigger against an orderable rank. A late moderation removal changes a
 * review's visibility; it never moves the cycle backwards.</p>
 */
public enum ReviewCycleState {
    /** The window is open and nobody has submitted. */
    OPEN,
    /** One side has submitted and is sealed. */
    ONE_SIDED_SEALED,
    /** Reveal conditions are met and a worker is acting. */
    REVEALING,
    /** Reveal happened, once, under a recorded epoch. */
    REVEALED,
    /** The window closed with nothing submitted. */
    CLOSED_EMPTY,
    /** A booking correction replaced this cycle. */
    SUPERSEDED;

    /**
     * Orderable form of this state, written to {@code review_cycles.state_rank}.
     *
     * <p>A check constraint pairs the two so they cannot disagree, and the monotonicity guard
     * compares ranks rather than parsing names. {@code CLOSED_EMPTY} shares a rank with
     * {@code REVEALED} because both are terminal outcomes of the same window.</p>
     *
     * @return rank between zero and four
     */
    public short rank() {
        return switch (this) {
            case OPEN -> 0;
            case ONE_SIDED_SEALED -> 1;
            case REVEALING -> 2;
            case REVEALED, CLOSED_EMPTY -> 3;
            case SUPERSEDED -> 4;
        };
    }
}
