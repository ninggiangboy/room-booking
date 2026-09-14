package dev.ngb.backend.trust.internal.model.decision;

/**
 * The operational standing of a decision whose outcome never changes.
 *
 * <p>The outcome is frozen by trigger. What moves is this projection, so "what was decided" and
 * "whether it still applies" stay separable.</p>
 */
public enum RiskDecisionProjection {
    /** Still applies. */
    EFFECTIVE,
    /** Its condition was met, such as a challenge that passed. */
    SATISFIED,
    /** Its window closed. */
    EXPIRED,
    /** A later decision replaced it. */
    SUPERSEDED;
}
