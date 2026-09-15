package dev.ngb.backend.trust.internal.model.intervention;

/**
 * How one try at a challenge ended.
 */
public enum ChallengeAttemptOutcome {
    /** The proof was accepted. */
    PASSED,
    /** The proof was rejected. */
    FAILED,
    /** The subject did not finish. */
    ABANDONED,
    /** The attempt could not be assessed. */
    ERRORED
}
