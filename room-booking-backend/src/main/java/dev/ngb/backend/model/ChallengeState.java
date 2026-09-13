package dev.ngb.backend.model;

/**
 * Where a challenge stands.
 *
 * <p>Stored beside an orderable rank so monotonicity is one integer comparison. The three endings
 * share the terminal rank: they are different endings, not different distances. Passing requires
 * evidence and an assurance level, and no attempt may be recorded once the challenge has ended.</p>
 */
public enum ChallengeState {
    /** Demanded but not yet begun. */
    REQUIRED,
    /** The subject began it. */
    STARTED,
    /** Proof was submitted and is being assessed. */
    SUBMITTED,
    /** Satisfied, with evidence. */
    PASSED,
    /** Not satisfied. */
    FAILED,
    /** The window closed first. */
    EXPIRED,
    /** Withdrawn before it was answered. */
    CANCELLED;

    /**
     * Orderable form of this state, where a higher number is further along.
     *
     * <p>Written to {@code risk_challenges.state_rank}, which a check constraint pairs with the
     * state itself. A trigger refuses any update that lowers it, and treats rank three as terminal
     * for every ending alike.</p>
     *
     * @return rank between zero and three
     */
    public short rank() {
        return switch (this) {
            case REQUIRED -> 0;
            case STARTED -> 1;
            case SUBMITTED -> 2;
            case PASSED, FAILED, EXPIRED, CANCELLED -> 3;
        };
    }
}
