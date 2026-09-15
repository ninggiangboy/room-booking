package dev.ngb.backend.trust.internal.model.intervention;

/**
 * How firmly a restriction is applied.
 */
public enum RestrictionEnforcementMode {
    /** The command is refused. */
    HARD_BLOCK,
    /** The command proceeds only after a step-up. */
    CHALLENGE_REQUIRED,
    /** The command is permitted at a reduced rate. */
    RATE_LIMITED,
    /** Nothing is blocked; the effect is measured. */
    SHADOW_MONITOR,
    /** The subject is told, and nothing is blocked. */
    DISCLOSURE_ONLY
}
