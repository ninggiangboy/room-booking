package dev.ngb.backend.model;

/**
 * Who took a moderation decision.
 *
 * <p>Paired by check constraint with the named decider: an automated decision has nobody behind it,
 * and a human one always names the person.</p>
 */
public enum ModerationDeciderType {
    /** Policy and detectors alone. */
    AUTOMATION,
    /** An authorized reviewer. */
    REVIEWER,
    /** An appeal reviewer. */
    APPEAL;
}
