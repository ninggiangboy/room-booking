package dev.ngb.backend.model;

/**
 * How an authentication attempt ended.
 *
 * <p>{@link #CHALLENGED} and {@link #BLOCKED} are distinct from {@link #FAILURE}: a challenged
 * attempt may still succeed, and counting it as a failure would let ordinary step-up traffic trip
 * the velocity controls meant to catch attackers.</p>
 */
public enum AuthAttemptOutcome {
    /** The attempt succeeded. */
    SUCCESS,
    /** The attempt was rejected on its merits. */
    FAILURE,
    /** Additional proof was demanded before a verdict. */
    CHALLENGED,
    /** Refused by a rate limit or risk decision without evaluating the credential. */
    BLOCKED
}
