package dev.ngb.backend.trust.internal.model.feature;

/**
 * Why a prediction carries no score.
 *
 * <p>A served prediction has a score and a fallback has none, paired by check constraint. Recording
 * a fallback with a number attached is how a timeout becomes a confident zero.</p>
 */
public enum ModelServingFallback {
    /** The model served normally and the score is present. */
    NONE,
    /** The model did not answer inside its latency budget. */
    TIMEOUT,
    /** Model contribution was disabled. */
    KILL_SWITCH,
    /** Required features were unavailable. */
    MISSING_FEATURES,
    /** The serving path was down. */
    UNAVAILABLE,
    /** Evaluated in shadow and deliberately not scored for this decision. */
    SHADOW_ONLY
}
